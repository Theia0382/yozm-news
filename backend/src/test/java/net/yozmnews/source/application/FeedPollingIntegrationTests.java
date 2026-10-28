package net.yozmnews.source.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.ArticleFetched;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.adapter.in.job.PollDueFeedsJob;
import net.yozmnews.source.adapter.in.job.PollFeedJob;
import net.yozmnews.source.domain.fetch.FetchFailureReason;
import net.yozmnews.source.domain.poll.FeedPollState;
import net.yozmnews.source.domain.poll.FeedPollStateRepository;
import net.yozmnews.source.domain.poll.ItemKey;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 수집 작업 흐름 — 실제 PostgreSQL · JobRunr 작업 서버와 WireMock 피드로 본다. 피드는 루프백의 WireMock 이라 수집 허용 대역에
 * {@code 127.0.0.1/32} 를 둔다. 작업 서버 poll 간격은 JobRunr 하한(5초)으로 줄인다. 1분 반복 작업은 테스트가 직접 부르므로 지운다 —
 * 남겨 두면 테스트 사이에 대상을 먼저 점유한다(등록은 {@code JobRunrConfigurationTests} 가 본다). 테스트는 트랜잭션으로 감싸지 않는다.
 */
@Import({ TestcontainersConfiguration.class, RegistryTestCatalog.class })
@SpringBootTest
@TestPropertySource(properties = { "yozmnews.collection.allowed-networks=127.0.0.1/32",
		"jobrunr.background-job-server.poll-interval-in-seconds=5" })
@ExtendWith(OutputCaptureExtension.class)
class FeedPollingIntegrationTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	@Autowired
	SourceRegistration registration;

	@Autowired
	SourceOperations operations;

	@Autowired
	PollScheduler scheduler;

	@Autowired
	PollDueFeedsJob pollDueFeedsJob;

	@Autowired
	PollFeedJob pollFeedJob;

	@Autowired
	JobScheduler jobs;

	@Autowired
	FeedPollStateRepository pollStates;

	@Autowired
	FetchedArticleRecorder recorder;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TransactionTemplate transaction;

	private TestFeeds feeds;

	@BeforeEach
	void reset() {
		this.feeds = new TestFeeds(this.registration, this.operations, this.jdbc, server);
		this.jobs.deleteRecurringJob("poll-due-feeds");
		new RegistryRows(this.jdbc).deleteAll();
		this.recorder.clear();
	}

	@Test
	void pollsThroughTheJobServerAndFollowsEachResult() {
		long feed = this.feeds.source("/a.xml").getFirst();
		server.stubFor(get("/a.xml").willReturn(TestFeeds.rss("a1", "a2").withHeader("ETag", "\"v1\"")));

		this.pollDueFeedsJob.run();
		await().atMost(TIMEOUT).until(() -> this.recorder.forFeed(feed).size() == 2 && state(feed).lease() == null);

		FeedPollState first = state(feed);
		assertThat(first.fetchState().etag()).isEqualTo("\"v1\"");
		assertThat(first.consecutiveFailures()).isZero();
		assertThat(first.previousItemKeys()).containsExactly(new ItemKey("g:a1"), new ItemKey("g:a2"));
		assertThat(first.lastNewItemAt()).isEqualTo(first.lastPolledAt());
		assertThat(first.nextPollAt()).isEqualTo(first.lastPolledAt().plus(Duration.ofMinutes(15)));
		ArticleFetched event = this.recorder.forFeed(feed).getFirst().event();
		assertThat(event.feedId()).isEqualTo(new FeedId(feed));
		// 저장한 시각은 PostgreSQL 이 마이크로초로 반올림한다 — 이벤트는 반올림 전의 그 수집 시각이다
		assertThat(event.fetchedAt()).isCloseTo(first.lastPolledAt(), within(1, ChronoUnit.MICROS));
		assertThat(this.recorder.forFeed(feed)).extracting(item -> item.event().article().guid())
			.containsExactly("a1", "a2");

		server.stubFor(get("/a.xml").withHeader("If-None-Match", equalTo("\"v1\""))
			.willReturn(aResponse().withStatus(304).withHeader("ETag", "\"v2\"")));
		pollNow(feed);
		FeedPollState notModified = state(feed);
		assertThat(this.recorder.forFeed(feed)).hasSize(2);
		assertThat(notModified.fetchState().etag()).isEqualTo("\"v2\"");
		assertThat(notModified.previousItemKeys()).containsExactly(new ItemKey("g:a1"), new ItemKey("g:a2"));

		server.stubFor(get("/a.xml").willReturn(TestFeeds.rss("a1", "a2", "a3").withHeader("ETag", "\"v3\"")));
		pollNow(feed);
		assertThat(this.recorder.forFeed(feed)).extracting(item -> item.event().article().guid())
			.containsExactly("a1", "a2", "a3");

		server.stubFor(get("/a.xml").willReturn(aResponse().withStatus(500)));
		pollNow(feed);
		FeedPollState temporary = state(feed);
		assertThat(temporary.consecutiveFailures()).isEqualTo(1);
		assertThat(temporary.lastError().reason()).isEqualTo(FetchFailureReason.SERVER_ERROR);
		assertThat(temporary.lastError().httpStatus()).isEqualTo(500);
		assertThat(temporary.nextPollAt()).isEqualTo(temporary.lastPolledAt().plus(Duration.ofMinutes(30)));

		server.stubFor(get("/a.xml").willReturn(aResponse().withStatus(404)));
		pollNow(feed);
		FeedPollState permanent = state(feed);
		assertThat(permanent.consecutiveFailures()).isEqualTo(2);
		assertThat(permanent.lastError().reason()).isEqualTo(FetchFailureReason.NOT_FOUND);
		assertThat(permanent.nextPollAt()).isEqualTo(permanent.lastPolledAt().plus(Duration.ofHours(1)));
		assertThat(permanent.fetchState().etag()).isEqualTo("\"v3\"");
	}

	@Test
	void fetchesOutsideADatabaseTransaction() {
		long feed = this.feeds.source("/slow.xml").getFirst();
		server.stubFor(get("/slow.xml").willReturn(TestFeeds.rss("s1").withFixedDelay(2_000)));
		ClaimedPoll claim = claim(feed);

		CompletableFuture<Void> polling = CompletableFuture
			.runAsync(() -> this.pollFeedJob.run(feed, claim.token()));
		await().atMost(TIMEOUT).until(() -> this.feeds.requests("/slow.xml") == 1);

		List<Long> locked = this.transaction.execute(status -> this.jdbc
			.sql("select feed_id from feed_poll_states where feed_id = :feed for update nowait")
			.param("feed", feed)
			.query(Long.class)
			.list());
		int idleInTransaction = this.jdbc.sql("""
				select count(*) from pg_stat_activity
				where datname = current_database() and pid <> pg_backend_pid() and state like 'idle in transaction%'
				""").query(Integer.class).single();
		polling.join();

		assertThat(locked).containsExactly(feed);
		assertThat(idleInTransaction).isZero();
		assertThat(this.recorder.forFeed(feed)).hasSize(1);
	}

	@Test
	void aRepeatedStaleOrDisabledJobChangesNothing() {
		long feed = this.feeds.source("/r.xml").getFirst();
		server.stubFor(get("/r.xml").willReturn(TestFeeds.rss("r1")));

		ClaimedPoll first = claim(feed);
		this.pollFeedJob.run(feed, first.token());
		this.pollFeedJob.run(feed, first.token());
		assertThat(this.feeds.requests("/r.xml")).isEqualTo(1);
		assertThat(this.recorder.forFeed(feed)).hasSize(1);

		makeDue(feed);
		ClaimedPoll second = claim(feed);
		assertThat(this.scheduler.claimDueFeeds()).isEmpty();

		makeDue(feed);
		ClaimedPoll third = claim(feed);
		this.pollFeedJob.run(feed, second.token());
		assertThat(this.feeds.requests("/r.xml")).isEqualTo(1);

		this.operations.disableFeed(new FeedId(feed));
		this.pollFeedJob.run(feed, third.token());
		assertThat(this.feeds.requests("/r.xml")).isEqualTo(1);
		assertThat(state(feed).lease().token()).isEqualTo(third.token());
	}

	@Test
	void pollsManySourcesAtOnceOnVirtualThreads() {
		List<String> paths = new ArrayList<>();
		List<Long> polled = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			String path = "/v" + i + ".xml";
			paths.add(path);
			polled.add(this.feeds.source(path).getFirst());
			server.stubFor(get(path).willReturn(TestFeeds.rss("v" + i).withFixedDelay(1_000)));
		}

		this.pollDueFeedsJob.run();
		await().atMost(TIMEOUT).until(() -> polled.stream().allMatch(feed -> this.recorder.forFeed(feed).size() == 1));

		List<Instant> received = this.feeds.receivedAt(paths);
		assertThat(received).hasSize(8);
		assertThat(Duration.between(received.getFirst(), received.getLast())).isLessThan(Duration.ofSeconds(1));
		assertThat(polled.stream().flatMap(feed -> this.recorder.forFeed(feed).stream()))
			.allMatch(FetchedArticleRecorder.Recorded::virtualThread);
	}

	@Test
	void defersAFeedDroppedFromTheProfileWithoutPolling(CapturedOutput output) {
		long kept = this.feeds.source("/d.xml").getFirst();
		long source = this.jdbc.sql("select source_id from feeds where id = :id")
			.param("id", kept)
			.query(Long.class)
			.single();
		long gone = new RegistryRows(this.jdbc).insertFeed(source, "gone");
		this.jdbc.sql("update feeds set enabled = true where id = :id").param("id", gone).update();
		this.jdbc.sql("""
				insert into feed_poll_states (feed_id, next_poll_at, consecutive_failures, overflow_count)
				values (:feed, now(), 0, 0)
				""").param("feed", gone).update();
		Instant before = Instant.now();

		List<ClaimedPoll> claims = this.scheduler.claimDueFeeds();
		Instant after = Instant.now();

		assertThat(claims).extracting(claim -> claim.feedId().value()).containsExactly(kept);
		FeedPollState deferred = state(gone);
		assertThat(deferred.lease()).isNull();
		assertThat(deferred.nextPollAt()).isBetween(before.plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS),
				after.plus(Duration.ofHours(1)).plusMillis(1));
		assertThat(output).contains("프로필에서 피드를 얻지 못해 수집을 미룬다").contains("gone");
	}

	@Test
	void waitsForTheScheduledTimeBeforeRequesting() {
		long feed = this.feeds.source("/w.xml").getFirst();
		server.stubFor(get("/w.xml").willReturn(TestFeeds.rss("w1")));
		UUID token = UUID.randomUUID();
		Instant scheduled = Instant.now().plusSeconds(3).truncatedTo(ChronoUnit.MILLIS);
		writeLease(feed, token, scheduled, scheduled.plus(Duration.ofMinutes(10)));

		this.pollFeedJob.run(feed, token);

		assertThat(this.feeds.receivedAt(List.of("/w.xml")).getFirst()).isAfterOrEqualTo(scheduled);
	}

	@Test
	void ignoresFeedsOfADisabledSourceAndDisabledFeeds() {
		long kept = this.feeds.source("/kept.xml").getFirst();
		long disabledFeed = this.feeds.source("/off.xml").getFirst();
		server.stubFor(get("/kept.xml").willReturn(TestFeeds.rss("k1")));
		this.operations.disableFeed(new FeedId(disabledFeed));
		SourceId source = sourceOf(kept);
		FeedPollState offBefore = state(disabledFeed);
		assertThat(offBefore.lease()).isNull();
		assertThat(offBefore.nextPollAt()).isBeforeOrEqualTo(Instant.now());

		List<ClaimedPoll> first = this.scheduler.claimDueFeeds();
		ClaimedPoll claim = claimOf(first, kept);
		this.operations.disableSource(source);
		this.pollFeedJob.run(kept, claim.token());
		FeedPollState afterRun = state(kept);
		makeDue(kept);
		List<ClaimedPoll> whileDisabled = this.scheduler.claimDueFeeds();
		this.operations.enableSource(source);
		List<ClaimedPoll> afterEnabled = this.scheduler.claimDueFeeds();

		assertThat(first).extracting(poll -> poll.feedId().value()).containsExactly(kept);
		assertThat(this.feeds.requests("/kept.xml")).isZero();
		assertThat(afterRun.lease().token()).isEqualTo(claim.token());
		assertThat(whileDisabled).isEmpty();
		assertThat(afterEnabled).extracting(poll -> poll.feedId().value()).containsExactly(kept);
		assertThat(state(disabledFeed).lease()).isNull();
	}

	@Test
	void discardsTheResultWhenTheLeaseIsLostDuringTheFetch(CapturedOutput output) {
		long feed = this.feeds.source("/lost.xml").getFirst();
		server.stubFor(get("/lost.xml").willReturn(TestFeeds.rss("l1").withFixedDelay(2_000)));
		ClaimedPoll first = claim(feed);

		CompletableFuture<Void> polling = CompletableFuture.runAsync(() -> this.pollFeedJob.run(feed, first.token()));
		await().atMost(TIMEOUT).until(() -> this.feeds.requests("/lost.xml") == 1);
		makeDue(feed);
		ClaimedPoll second = claim(feed);
		polling.join();

		assertThat(this.recorder.forFeed(feed)).isEmpty();
		assertThat(this.feeds.requests("/lost.xml")).isEqualTo(1);
		assertThat(state(feed).lease().token()).isEqualTo(second.token());
		assertThat(output).contains("수집하는 사이 점유를 잃어 결과를 버린다 — 피드 " + feed);
	}

	@Test
	void skipsTheFetchWhenTheReservationOutlastsTheLease(CapturedOutput output) {
		long late = this.feeds.source("/late.xml").getFirst();
		long fits = this.feeds.source("/fits.xml").getFirst();
		server.stubFor(get("/late.xml").willReturn(TestFeeds.rss("t1")));
		server.stubFor(get("/fits.xml").willReturn(TestFeeds.rss("t2")));
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		UUID lateToken = UUID.randomUUID();
		UUID fitsToken = UUID.randomUUID();
		writeLease(late, lateToken, now, now.plusSeconds(5));
		writeLease(fits, fitsToken, now, now.plus(Duration.ofMinutes(10)));

		Instant before = Instant.now();
		this.pollFeedJob.run(late, lateToken);
		Instant after = Instant.now();
		this.pollFeedJob.run(fits, fitsToken);

		assertThat(this.feeds.requests("/late.xml")).isZero();
		assertThat(output).contains("같은 소스의 요청 예약이 점유 만료를 넘는다 — 피드 " + late);
		assertThat(state(late).lease().token()).isEqualTo(lateToken);
		// 건너뛴 수집도 그 예약을 쓴 것으로 남는다 — 시작(실행 중의 지금) + 10초
		assertThat(nextFetchAt(sourceOf(late))).isBetween(before.plusSeconds(10).truncatedTo(ChronoUnit.MICROS),
				after.plusSeconds(10).plus(1, ChronoUnit.MICROS));
		assertThat(this.feeds.requests("/fits.xml")).isEqualTo(1);
	}

	private ClaimedPoll claim(long feed) {
		return claimOf(this.scheduler.claimDueFeeds(), feed);
	}

	private static ClaimedPoll claimOf(List<ClaimedPoll> claims, long feed) {
		return claims.stream().filter(claim -> claim.feedId().value() == feed).findFirst().orElseThrow();
	}

	/** 다음 수집 시각을 지금 이전으로 당기고 점유해 바로 수집한다 */
	private void pollNow(long feed) {
		makeDue(feed);
		ClaimedPoll claim = claim(feed);
		this.pollFeedJob.run(feed, claim.token());
	}

	private void makeDue(long feed) {
		this.jdbc.sql("update feed_poll_states set next_poll_at = now() - interval '1 second' where feed_id = :feed")
			.param("feed", feed)
			.update();
	}

	/** 점유를 직접 쓴다 — 토큰 · 예약 시각 · 만료(다음 수집 시각) */
	private void writeLease(long feed, UUID token, Instant scheduled, Instant expires) {
		this.jdbc.sql("""
				update feed_poll_states set lease_token = :token, lease_scheduled_at = :scheduled, next_poll_at = :expires
				where feed_id = :feed
				""")
			.param("token", token)
			.param("scheduled", scheduled.atOffset(ZoneOffset.UTC))
			.param("expires", expires.atOffset(ZoneOffset.UTC))
			.param("feed", feed)
			.update();
	}

	private SourceId sourceOf(long feed) {
		return new SourceId(this.jdbc.sql("select source_id from feeds where id = :feed")
			.param("feed", feed)
			.query(Long.class)
			.single());
	}

	private Instant nextFetchAt(SourceId source) {
		return this.jdbc.sql("select next_fetch_at from source_fetch_slots where source_id = :source")
			.param("source", source.value())
			.query(OffsetDateTime.class)
			.single()
			.toInstant();
	}

	private FeedPollState state(long feed) {
		return this.pollStates.find(new FeedId(feed)).orElseThrow();
	}

}
