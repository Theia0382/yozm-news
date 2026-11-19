package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.yozmnews.TestClocks;
import net.yozmnews.TestClocks.MovableClock;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestInputs;
import net.yozmnews.digest.DigestRounds;
import net.yozmnews.digest.DigestStatus;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.adapter.out.fake.FakeDigestGenerator;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.candidate.DigestCandidateRepository;
import net.yozmnews.digest.domain.content.DigestSectionRepository;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.digest.DigestLedger;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.digest.domain.digest.DigestRepository;
import net.yozmnews.digest.domain.digest.DigestTrigger;
import net.yozmnews.event.EventArticles;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.DigestFailed;
import net.yozmnews.user.DigestFailureCause;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 멈춘 다이제스트 거두기 — 실제 PostgreSQL 에서, 등록한 지 15분이 지나도 시작되지 않은 다이제스트는 새 토큰으로 다시 등록하고 만든 지 1시간이
 * 지났으면 끝나지 않은 실패로 끝내며, 시작한 지 40분이 지나도 생성 중인 다이제스트는 끝나지 않은 실패로 끝내는지 본다. 경계 양쪽을 고정 시계로
 * 본다. 거두기가 생성 중인 실행보다 먼저 끝내면 그 실행의 저장은 아무것도 쓰지 않는다. 등록은 포트의 기록 대체물이 받는다. 기대값은 손으로 쓴
 * 리터럴이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@RecordApplicationEvents
class StalledDigestsTests {

	private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

	private static final LlmModel MODEL = new LlmModel(Provider.ANTHROPIC, "claude-sonnet-5-5", 100_000, true);

	@Autowired
	JobScheduler digestJobs;

	@Autowired
	DigestRepository digests;

	@Autowired
	DigestSectionRepository sections;

	@Autowired
	DigestCandidateRepository candidates;

	@Autowired
	ArticleDirectory articles;

	@Autowired
	DigestLedger ledger;

	@Autowired
	DigestEndings endings;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	ApplicationEvents events;

	@Autowired
	JdbcClient jdbc;

	private final List<Map.Entry<Long, UUID>> enqueued = Collections.synchronizedList(new ArrayList<>());

	private final List<Long> articleIds = new ArrayList<>();

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
		this.enqueued.clear();
	}

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from digests").update();
		for (Long id : this.articleIds) {
			this.jdbc.sql("delete from article_feed_entries where article_id = :id").param("id", id).update();
			this.jdbc.sql("delete from articles where id = :id").param("id", id).update();
		}
		this.articleIds.clear();
	}

	private StalledDigests stalled(Instant now) {
		return stalled(TestClocks.fixed(now));
	}

	private StalledDigests stalled(AppClock clock) {
		DigestDispatch dispatch = new DigestDispatch((id, token) -> this.enqueued.add(Map.entry(id.value(), token)));
		return new StalledDigests(this.digests, this.ledger, this.endings, dispatch, this.transaction, clock);
	}

	@Test
	void aDigestNotStartedFifteenMinutesAfterItsDispatchIsDispatchedAgainWithANewToken() {
		UUID old = UUID.fromString("9e3a7c21-4b8d-4f16-a0c2-5e7d1b9f3a64");
		long stale = scheduled("2026-11-19", NOW.minusSeconds(16 * 60), NOW.minusSeconds(16 * 60), old);
		long fresh = scheduled("2026-11-20", NOW.minusSeconds(14 * 60), NOW.minusSeconds(14 * 60), UUID.randomUUID());

		stalled(NOW).runRound();

		Map<String, Object> again = row(stale);
		assertThat(again).containsEntry("status", "SCHEDULED").containsEntry("dispatched_at", Timestamp.from(NOW));
		assertThat(again.get("dispatch_token")).isNotEqualTo(old);
		assertThat(this.enqueued).containsExactly(Map.entry(stale, (UUID) again.get("dispatch_token")));
		// 대조 — 14분 된 등록은 그대로
		assertThat(row(fresh)).containsEntry("dispatched_at", Timestamp.from(NOW.minusSeconds(14 * 60)));
		assertThat(this.events.stream(DigestFailed.class)).isEmpty();
	}

	@Test
	void aDigestNotStartedAnHourAfterItWasMadeEndsInterruptedWithoutDispatch() {
		long late = scheduled("2026-11-19", NOW.minusSeconds(61 * 60), NOW.minusSeconds(16 * 60), UUID.randomUUID());
		long early = scheduled("2026-11-20", NOW.minusSeconds(59 * 60), NOW.minusSeconds(16 * 60), UUID.randomUUID());

		stalled(NOW).runRound();

		assertThat(row(late)).containsEntry("status", "FAILED")
			.containsEntry("error_code", "INTERRUPTED")
			.containsEntry("error_message", "시작 기한 안에 생성이 시작되지 않았다")
			.containsEntry("completed_at", Timestamp.from(NOW));
		// 대조 — 만든 지 59분이면 다시 등록한다
		assertThat(row(early)).containsEntry("status", "SCHEDULED");
		assertThat(this.enqueued).extracting(Map.Entry::getKey).containsExactly(early);
		assertThat(this.events.stream(DigestFailed.class)).containsExactly(new DigestFailed(new UserId(42), late,
				LocalDate.parse("2026-11-19"), DigestFailureCause.TEMPORARY, NOW));
	}

	@Test
	void aDigestRunningFortyMinutesEndsInterruptedAndKeepsItsModel() {
		long stuck = running("2026-11-19", NOW.minusSeconds(41 * 60));
		long busy = running("2026-11-20", NOW.minusSeconds(39 * 60));

		stalled(NOW).runRound();

		assertThat(row(stuck)).containsEntry("status", "FAILED")
			.containsEntry("error_code", "INTERRUPTED")
			.containsEntry("error_message", "임대 안에 생성이 끝나지 않았다")
			.containsEntry("provider", "anthropic")
			.containsEntry("model", "claude-sonnet-5-5");
		assertThat(row(stuck).get("duration_ms")).isNull();
		// 대조 — 39분이면 그대로 생성 중
		assertThat(row(busy)).containsEntry("status", "RUNNING");
		assertThat(this.enqueued).isEmpty();
		assertThat(this.events.stream(DigestFailed.class)).extracting(DigestFailed::digestId, DigestFailed::cause)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(stuck, DigestFailureCause.TEMPORARY));
	}

	/**
	 * 측정용 시계를 넘겨도 진행 중인 다이제스트는 멈춘 것으로 보지 않는다 — 기다림(15분) · 시작 기한(1시간) · 생성 임대(40분)는 시계가 넘긴
	 * 시간을 뺀 경과로 잰다. 둘 다 NOW 1분 전에 등록 · 시작했다.
	 */
	@Test
	void aJumpOfTheClockDoesNotStallDigestsInProgress() {
		Instant before = NOW.minusSeconds(60);
		long waiting = scheduled("2026-11-19", before, before, UUID.randomUUID());
		long busy = running("2026-11-20", before);
		MovableClock clock = TestClocks.movable(NOW).jump(Duration.ofHours(24));

		stalled(clock).runRound();

		assertThat(row(waiting)).containsEntry("status", "SCHEDULED").containsEntry("dispatched_at", Timestamp.from(before));
		assertThat(row(busy)).containsEntry("status", "RUNNING");
		assertThat(this.enqueued).isEmpty();
		assertThat(this.events.stream(DigestFailed.class)).isEmpty();

		// 대조 — 넘긴 뒤 실제로 40분이 흐르면 경과 41분이라 생성 임대가 지나 끝내고, 생성 대기는 다시 등록한다(만든 지 41분)
		clock.advance(Duration.ofMinutes(40));
		stalled(clock).runRound();

		assertThat(row(busy)).containsEntry("status", "FAILED").containsEntry("error_code", "INTERRUPTED");
		assertThat(row(waiting)).containsEntry("status", "SCHEDULED")
			.containsEntry("dispatched_at", Timestamp.from(Instant.parse("2026-11-20T12:40:00Z")));
		assertThat(this.enqueued).extracting(Map.Entry::getKey).containsExactly(waiting);
	}

	@Test
	void finishedDigestsAreLeftAlone() {
		long done = scheduled("2026-11-19", NOW.minusSeconds(3 * 3600), NOW.minusSeconds(3 * 3600), UUID.randomUUID());
		this.jdbc.sql("update digests set status = 'EMPTY', started_at = :at, completed_at = :at, provider = 'openai', model = 'm' where id = :id")
			.param("at", Timestamp.from(NOW.minusSeconds(3 * 3600)))
			.param("id", done)
			.update();

		stalled(NOW).runRound();

		assertThat(row(done)).containsEntry("status", "EMPTY");
		assertThat(this.enqueued).isEmpty();
		assertThat(this.events.stream(DigestFailed.class)).isEmpty();
	}

	@Test
	void whenTheRoundEndsARunningDigestFirstTheRunWritesNothing() {
		for (int index = 1; index <= 2; index++) {
			this.articleIds.add(EventArticles.insert(this.jdbc, "거두기 시험 기사 " + index, null));
		}
		FakeDigestGenerator fake = new FakeDigestGenerator();
		// 생성은 NOW 에 시작하고, 생성기 안에서 임대가 지난 시각의 거두기가 돈다
		DigestGeneration generation = new DigestGeneration(this.digests, this.sections, this.candidates, this.articles,
				request -> {
					stalled(NOW.plusSeconds(41 * 60)).runRound();
					return fake.generate(request);
				}, this.endings, this.transaction, TestClocks.fixed(NOW));
		DigestId id = generation.schedule(new UserId(42), LocalDate.parse("2026-11-19"),
				new DigestPeriod(Instant.parse("2026-11-18T12:00:00Z"), NOW), DigestTrigger.MANUAL).id();
		List<DigestCandidate> list = List.of(
				new DigestCandidate(new ArticleId(this.articleIds.get(0)), SelectionReason.SUBSCRIBED),
				new DigestCandidate(new ArticleId(this.articleIds.get(1)), SelectionReason.SUBSCRIBED));

		assertThat(generation.run(id, MODEL, DigestInputs.of(list))).isEqualTo(DigestStatus.FAILED);

		assertThat(row(id.value())).containsEntry("error_code", "INTERRUPTED");
		assertThat(this.jdbc.sql("select count(*) from digest_sections where digest_id = :id")
			.param("id", id.value())
			.query(Long.class)
			.single()).isZero();
		assertThat(this.events.stream(DigestFailed.class)).hasSize(1);
	}

	/** 사용자 42 의 생성을 기다리는 다이제스트 — 만든 시각 · 등록 시각 · 토큰 */
	private long scheduled(String date, Instant createdAt, Instant dispatchedAt, UUID token) {
		return this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, created_at,
				    dispatch_token, dispatched_at, version)
				values (42, cast(:date as date), cast(:date as date) - interval '1 day', cast(:date as date), 'SCHEDULED',
				    'MANUAL', :created, :token, :dispatched, 0)
				returning id
				""")
			.param("date", date)
			.param("created", Timestamp.from(createdAt))
			.param("token", token)
			.param("dispatched", Timestamp.from(dispatchedAt))
			.query(Long.class)
			.single();
	}

	/** 사용자 42 의 생성 중인 다이제스트 — 시작 시각 */
	private long running(String date, Instant startedAt) {
		long id = scheduled(date, startedAt.minusSeconds(60), startedAt.minusSeconds(60), UUID.randomUUID());
		this.jdbc.sql("""
				update digests set status = 'RUNNING', started_at = :started, provider = 'anthropic',
				    model = 'claude-sonnet-5-5', candidate_count = 3
				where id = :id
				""").param("started", Timestamp.from(startedAt)).param("id", id).update();
		return id;
	}

	private Map<String, Object> row(long id) {
		return this.jdbc.sql("""
				select status, provider, model, duration_ms, error_code, error_message, completed_at, dispatch_token,
				       dispatched_at
				from digests where id = :id
				""").param("id", id).query().singleRow();
	}

}
