package net.yozmnews.source.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchFailureReason;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.poll.FeedPollContext;
import net.yozmnews.source.domain.poll.FeedPollState;
import net.yozmnews.source.domain.poll.ItemKey;
import net.yozmnews.source.domain.poll.LastError;
import net.yozmnews.source.domain.poll.Lease;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 수집 상태 저장소의 왕복과 대상 잠금 — Flyway 가 적용된 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: source-R3 · source-R15 · source-R20 · source-R48
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcFeedPollStateRepository.class })
class JdbcFeedPollStateRepositoryTests {

	private static final Instant AT = Instant.parse("2026-10-28T10:00:12.345678Z");

	@Autowired
	JdbcClient jdbc;

	@Autowired
	JdbcFeedPollStateRepository repository;

	@Autowired
	PlatformTransactionManager transactionManager;

	private long source;

	@BeforeEach
	void createSource() {
		this.source = this.jdbc.sql("""
				insert into sources (preset_key, enabled, version, created_at) values ('alpha', false, 0, now()) returning id
				""").query(Long.class).single();
	}

	@Test
	void roundTripsAnInitialState() {
		FeedPollState initial = FeedPollState.initial(feed("a"), AT);

		this.repository.insertIfAbsent(initial);

		assertThat(this.repository.find(initial.feedId())).contains(initial);
		assertThat(this.repository.find(initial.feedId()).orElseThrow().previousItemKeys()).isNull();
	}

	@Test
	void roundTripsEveryColumn() {
		FeedPollState state = new FeedPollState(feed("b"), Instant.parse("2026-10-28T10:10:10Z"),
				new Lease(UUID.fromString("0a1b2c3d-0000-4000-8000-000000000001"), Instant.parse("2026-10-28T10:00:10Z")),
				new FetchState("W/\"abc\"", "Wed, 28 Oct 2026 10:00:00 GMT"), AT, Instant.parse("2026-10-27T08:00:00Z"),
				Instant.parse("2026-10-26T07:00:00Z"), Instant.parse("2026-10-27T03:00:00Z"), 3, 1,
				new LastError(FetchFailureReason.SERVER_ERROR, "서버 오류 응답 (HTTP 503)", 503, AT),
				URI.create("https://news.example/rss/new"), Instant.parse("2026-10-25T06:00:00Z"), 4,
				List.of(new ItemKey("g:urn:1"), new ItemKey("l:https://news.example/2"), new ItemKey("g:urn:3")));

		this.repository.insertIfAbsent(state);

		assertThat(this.repository.find(state.feedId())).contains(state);
	}

	@Test
	void keepsAnEmptyItemKeyListApartFromNone() {
		FeedPollState empty = withKeys(feed("c"), List.of());
		FeedPollState none = withKeys(feed("d"), null);

		this.repository.insertIfAbsent(empty);
		this.repository.insertIfAbsent(none);

		assertThat(this.repository.find(empty.feedId()).orElseThrow().previousItemKeys()).isEmpty();
		assertThat(this.repository.find(none.feedId()).orElseThrow().previousItemKeys()).isNull();
	}

	@Test
	void insertingTwiceKeepsTheFirstRow() {
		FeedId feed = feed("e");
		FeedPollState first = FeedPollState.initial(feed, AT);

		this.repository.insertIfAbsent(first);
		this.repository.insertIfAbsent(FeedPollState.initial(feed, Instant.parse("2026-10-29T00:00:00Z")));

		assertThat(this.repository.find(feed)).contains(first);
	}

	@Test
	void findsNothingForAFeedWithoutState() {
		assertThat(this.repository.find(feed("f"))).isEmpty();
	}

	@Test
	void collectionWritesLeaveTheFeedStatusAlone() {
		FeedId feed = feed("g");
		UUID token = UUID.fromString("0a1b2c3d-0000-4000-8000-000000000002");
		this.repository.insertIfAbsent(FeedPollState.initial(feed, AT).claim(new Lease(token, AT), AT));
		this.jdbc.sql("""
				update feed_poll_states set status = 'STALE', status_changed_at = '2026-10-29T00:00:00Z' where feed_id = :feed
				""").param("feed", feed.value()).update();

		boolean replaced = this.repository.replaceIfLeased(token, FeedPollState.initial(feed, AT));
		Status afterReplace = status(feed);
		this.repository.saveLocked(FeedPollState.initial(feed, Instant.parse("2026-10-29T12:00:00Z")));
		Status afterSave = status(feed);
		FeedId fresh = feed("h");
		this.repository.insertIfAbsent(FeedPollState.initial(fresh, AT));

		assertThat(replaced).isTrue();
		assertThat(afterReplace).isEqualTo(new Status("STALE", Instant.parse("2026-10-29T00:00:00Z")));
		assertThat(this.repository.find(feed).orElseThrow().nextPollAt())
			.isEqualTo(Instant.parse("2026-10-29T12:00:00Z"));
		assertThat(afterSave).isEqualTo(new Status("STALE", Instant.parse("2026-10-29T00:00:00Z")));
		assertThat(status(fresh)).isEqualTo(new Status("ACTIVE", null));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void aSecondRoundSkipsRowsLockedByTheFirst() throws InterruptedException {
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		long enabledSource = this.jdbc.sql("""
				insert into sources (preset_key, enabled, version, created_at) values ('locking', true, 0, now()) returning id
				""").query(Long.class).single();
		FeedId feed = new FeedId(this.jdbc.sql("""
				insert into feeds (source_id, feed_key, enabled, version, created_at)
				values (:source, 'due', true, 0, now()) returning id
				""").param("source", enabledSource).query(Long.class).single());
		this.repository.insertIfAbsent(FeedPollState.initial(feed, AT));
		// 처음 상태의 다음 수집 시각은 만든 시각(AT)이라, 이 시각에 대상이다
		Instant now = AT;
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			CompletableFuture<List<FeedPollContext>> holder = CompletableFuture
				.supplyAsync(() -> transaction.execute(status -> {
					List<FeedPollContext> due = this.repository.lockDue(now, 10);
					locked.countDown();
					awaitQuietly(release);
					return due;
				}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			// 앞 회차가 잡은 행은 기다리지 않고 건너뛴다 — 기다린다면 200ms 뒤 55P03 으로 실패한다
			List<FeedPollContext> whileLocked = transaction.execute(status -> {
				this.jdbc.sql("set local lock_timeout = '200ms'").update();
				return this.repository.lockDue(now, 10);
			});
			release.countDown();
			List<FeedPollContext> firstRound = holder.join();
			List<FeedPollContext> afterRelease = transaction.execute(status -> this.repository.lockDue(now, 10));

			assertThat(firstRound).extracting(context -> context.state().feedId()).containsExactly(feed);
			assertThat(whileLocked).isEmpty();
			assertThat(afterRelease).extracting(context -> context.state().feedId()).containsExactly(feed);
		}
		finally {
			release.countDown();
			this.jdbc.sql("delete from feed_poll_states where feed_id = :feed").param("feed", feed.value()).update();
			this.jdbc.sql("delete from feeds where id = :feed").param("feed", feed.value()).update();
			this.jdbc.sql("delete from sources where id in (:sources)")
				.param("sources", List.of(enabledSource, this.source))
				.update();
		}
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private Status status(FeedId feed) {
		return this.jdbc.sql("select status, status_changed_at from feed_poll_states where feed_id = :feed")
			.param("feed", feed.value())
			.query((rs, row) -> {
				OffsetDateTime changedAt = rs.getObject("status_changed_at", OffsetDateTime.class);
				return new Status(rs.getString("status"), (changedAt != null) ? changedAt.toInstant() : null);
			})
			.single();
	}

	/** 수집 경로가 쓰지 않는 피드 상태 두 열 */
	private record Status(String status, @Nullable Instant changedAt) {
	}

	private FeedId feed(String key) {
		return new FeedId(this.jdbc.sql("""
				insert into feeds (source_id, feed_key, enabled, version, created_at)
				values (:source, :key, false, 0, now()) returning id
				""").param("source", this.source).param("key", key).query(Long.class).single());
	}

	private static FeedPollState withKeys(FeedId feed, @Nullable List<ItemKey> keys) {
		return new FeedPollState(feed, AT, null, FetchState.NONE, null, null, null, null, 0, 0, null, null, null, 0,
				keys);
	}

}
