package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestClocks;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.digest.DigestRequestOutcome;
import net.yozmnews.digest.DigestRounds;
import net.yozmnews.digest.adapter.out.fake.FakeDigestGenerator;
import net.yozmnews.digest.domain.candidate.DigestCandidateRepository;
import net.yozmnews.digest.domain.content.DigestSectionRepository;
import net.yozmnews.digest.domain.digest.DigestLedger;
import net.yozmnews.digest.domain.digest.DigestOwnerLock;
import net.yozmnews.digest.domain.digest.DigestRepository;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.DigestModelDirectory;
import net.yozmnews.user.DigestScheduleDirectory;
import net.yozmnews.user.UserTables;
import net.yozmnews.user.UserTimezones;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 수동 생성 요청 — 실제 PostgreSQL 과 실제 시간대 · 쓸 모델 조회로, 오늘 다이제스트가 없으면 새 기간으로 만들고 있으면 같은 기간으로 다시
 * 만들며, 사용자 시간대의 하루에 세 번까지 받고, 진행 중이거나 쓸 모델이 없으면 세지 않고 거절하는지 본다. 같은 사용자의 요청 둘 · 요청과 일정
 * 회차가 겹쳐도 행은 하나다. 등록은 포트의 기록 대체물이 받는다. 시계는 고정한다.
 * <p>
 * 사용자는 `America/Los_Angeles`(PST, UTC−8) · 03:17 이다. 기대값은 손으로 셈한 리터럴이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class DigestRequestsTests {

	/** 2026-11-19 10:00 PST */
	private static final Instant NOW = Instant.parse("2026-11-19T18:00:00Z");

	@Autowired
	JobScheduler digestJobs;

	@Autowired
	UserTimezones timezones;

	@Autowired
	DigestModelDirectory models;

	@Autowired
	DigestScheduleDirectory schedules;

	@Autowired
	DigestLedger ledger;

	@Autowired
	DigestOwnerLock owners;

	@Autowired
	DigestRepository digests;

	@Autowired
	DigestSectionRepository sections;

	@Autowired
	DigestCandidateRepository candidates;

	@Autowired
	ArticleDirectory articles;

	@Autowired
	DigestEndings endings;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private final List<Map.Entry<Long, UUID>> enqueued = Collections.synchronizedList(new ArrayList<>());

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
		this.enqueued.clear();
	}

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from digests").update();
		this.jdbc.sql("delete from llm_credentials where user_id in (select id from users where email like 'digest-request-%')")
			.update();
		UserTables.deleteByEmail(this.jdbc, "digest-request-%");
	}

	private DigestDispatch dispatch() {
		return new DigestDispatch((id, token) -> this.enqueued.add(Map.entry(id.value(), token)));
	}

	/** 행을 만드는 시각도 같은 고정 시계다 — 오늘 횟수는 만든 시각으로 센다 */
	private DigestGeneration generation(Instant now) {
		return new DigestGeneration(this.digests, this.sections, this.candidates, this.articles,
				new FakeDigestGenerator(), this.endings, this.transaction, TestClocks.fixed(now));
	}

	private DigestRequestsService requests(Instant now) {
		return new DigestRequestsService(this.timezones, this.models, this.ledger, this.owners, generation(now),
				dispatch(), this.transaction, () -> now);
	}

	@Test
	void aFirstRequestMakesTodayUpToNowAndARequestOnTheSameDayMakesItAgain() {
		UserId user = user("digest-request-first@example.com", "VALID");

		assertThat(requests(NOW).request(user))
			.isEqualTo(new DigestRequestOutcome.Requested(LocalDate.parse("2026-11-19"), false));

		Map<String, Object> first = rows(user).getFirst();
		assertThat(first).containsEntry("trigger", "MANUAL")
			.containsEntry("status", "SCHEDULED")
			.containsEntry("digest_date", java.sql.Date.valueOf("2026-11-19"))
			.containsEntry("period_start", Timestamp.from(Instant.parse("2026-11-18T18:00:00Z")))
			.containsEntry("period_end", Timestamp.from(NOW));
		assertThat(this.enqueued).containsExactly(Map.entry((Long) first.get("id"), (UUID) first.get("dispatch_token")));

		// 생성 중이면 받지 않고 세지 않는다
		assertThat(requests(NOW.plusSeconds(60)).request(user)).isEqualTo(new DigestRequestOutcome.InProgress());
		assertThat(rows(user)).hasSize(1);

		// 끝난 뒤 같은 날 — 같은 날짜를 같은 기간으로 다시
		finish(first, "READY");
		assertThat(requests(NOW.plusSeconds(3600)).request(user))
			.isEqualTo(new DigestRequestOutcome.Requested(LocalDate.parse("2026-11-19"), true));
		Map<String, Object> again = rows(user).getLast();
		assertThat(again).containsEntry("trigger", "MANUAL")
			.containsEntry("period_start", Timestamp.from(Instant.parse("2026-11-18T18:00:00Z")))
			.containsEntry("period_end", Timestamp.from(NOW));
		assertThat(this.enqueued).hasSize(2);
	}

	@Test
	void threeRequestsADayInTheUsersZoneAndTheFourthWaitsForLocalMidnight() {
		UserId user = user("digest-request-limit@example.com", "VALID");
		// 2026-11-18 23:00 PST 에 만든 수동 셋 — 그날의 몫을 다 썼다
		for (int index = 0; index < 3; index++) {
			manualRow(user, "2026-11-18", Instant.parse("2026-11-19T07:00:00Z"));
		}

		// 23:59 PST — 아직 2026-11-18
		assertThat(requests(Instant.parse("2026-11-19T07:59:00Z")).request(user))
			.isEqualTo(new DigestRequestOutcome.DailyLimitReached(Instant.parse("2026-11-19T08:00:00Z")));
		assertThat(rows(user)).hasSize(3);
		assertThat(this.enqueued).isEmpty();

		// 대조 — 00:01 PST 의 2026-11-19 는 새 하루다
		assertThat(requests(Instant.parse("2026-11-19T08:01:00Z")).request(user))
			.isEqualTo(new DigestRequestOutcome.Requested(LocalDate.parse("2026-11-19"), false));
	}

	@Test
	void aRequestRefusedAsInProgressIsNotCounted() {
		UserId user = user("digest-request-counted@example.com", "VALID");
		manualRow(user, "2026-11-19", Instant.parse("2026-11-19T16:00:00Z"));
		manualRow(user, "2026-11-19", Instant.parse("2026-11-19T17:00:00Z"));

		assertThat(requests(NOW).request(user)).isInstanceOf(DigestRequestOutcome.Requested.class);
		// 셋째(위의 요청)가 생성 중 — 거절은 세지 않는다
		assertThat(requests(NOW.plusSeconds(60)).request(user)).isEqualTo(new DigestRequestOutcome.InProgress());
		this.jdbc.sql("update digests set status = 'EMPTY', started_at = now(), completed_at = now(), provider = 'openai', model = 'm' where user_id = :user and status = 'SCHEDULED'")
			.param("user", user.value())
			.update();
		// 대조 — 세 번을 다 썼다(거절된 것은 세지 않았으므로 정확히 셋)
		assertThat(requests(NOW.plusSeconds(120)).request(user)).isInstanceOf(DigestRequestOutcome.DailyLimitReached.class);
	}

	/** 하루 횟수는 수동 다이제스트만 센다 — 같은 날 만든 일정 다이제스트는 세지 않는다 */
	@Test
	void aScheduledDigestOfTheDayIsNotCounted() {
		UserId user = user("digest-request-scheduled@example.com", "VALID");
		// 2026-11-19 03:17 PST(11:17Z) 에 만든 일정 다이제스트 하나와 그 뒤의 수동 둘
		this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, created_at,
				    dispatch_token, dispatched_at, started_at, completed_at, provider, model, version)
				values (:user, date '2026-11-19', timestamptz '2026-11-18T11:17:00Z', timestamptz '2026-11-19T11:17:00Z',
				    'EMPTY', 'SCHEDULED', timestamptz '2026-11-19T11:17:00Z', gen_random_uuid(),
				    timestamptz '2026-11-19T11:17:00Z', timestamptz '2026-11-19T11:17:00Z', timestamptz '2026-11-19T11:17:00Z',
				    'openai', 'm', 0)
				""").param("user", user.value()).update();
		manualRow(user, "2026-11-19", Instant.parse("2026-11-19T16:00:00Z"));
		manualRow(user, "2026-11-19", Instant.parse("2026-11-19T17:00:00Z"));

		// 그날 넷째 행이지만 수동으로는 셋째다
		assertThat(requests(NOW).request(user))
			.isEqualTo(new DigestRequestOutcome.Requested(LocalDate.parse("2026-11-19"), true));
	}

	@Test
	void withoutAUsableModelOrAccountNothingIsMade() {
		UserId invalid = user("digest-request-invalid@example.com", "INVALID");

		assertThat(requests(NOW).request(invalid)).isEqualTo(new DigestRequestOutcome.NoUsableModel());
		assertThat(requests(NOW).request(new UserId(Long.MAX_VALUE))).isEqualTo(new DigestRequestOutcome.NoUsableModel());

		assertThat(rows(invalid)).isEmpty();
		assertThat(this.enqueued).isEmpty();
	}

	@Test
	void refusesToRunInsideATransaction() {
		UserId user = user("digest-request-tx@example.com", "VALID");

		assertThatIllegalStateException()
			.isThrownBy(() -> this.transaction.executeWithoutResult(status -> requests(NOW).request(user)));

		assertThat(rows(user)).isEmpty();
	}

	@Test
	void twoRequestsAtOnceMakeOneDigest() throws Exception {
		UserId user = user("digest-request-race@example.com", "VALID");
		List<DigestRequestOutcome> outcomes = race(() -> requests(NOW).request(user), () -> requests(NOW).request(user));

		assertThat(outcomes).containsExactlyInAnyOrder(
				new DigestRequestOutcome.Requested(LocalDate.parse("2026-11-19"), false),
				new DigestRequestOutcome.InProgress());
		assertThat(rows(user)).hasSize(1);
		assertThat(this.enqueued).hasSize(1);
	}

	/**
	 * 같은 사용자의 수동 요청과 일정 회차는 사용자 잠금으로 줄을 선다 — 요청이 진행 중 확인을 지나 행을 넣기 직전에 멈춘 사이 일정 회차가 돌아도
	 * 행은 하나다. 요청은 일정 회차가 끝나기를 최대 2초 기다린다 — 잠금이 있으면 일정 회차가 요청의 커밋을 기다리므로 기다림이 끝까지 가고, 요청이
	 * 커밋한 뒤 일정 회차는 그 날짜가 이미 있어 만들지 않는다. 잠금이 없으면 일정 회차가 먼저 행을 만들고 요청이 이어 넣어 둘이 된다.
	 */
	@Test
	void aRequestAndTheScheduleRoundOfTheSameUserMakeOneDigest() throws Exception {
		UserId user = user("digest-request-schedule@example.com", "VALID");
		// 생성 시각 03:17 PST 의 1분 뒤
		Instant at = Instant.parse("2026-11-19T11:18:00Z");
		DigestScheduling scheduling = new DigestScheduling(this.schedules, this.models, this.ledger, this.owners,
				generation(at), dispatch(), this.transaction, () -> at);
		CountDownLatch requestPaused = new CountDownLatch(1);
		CountDownLatch roundFinished = new CountDownLatch(1);
		// 요청의 마지막 읽기(직전 끝) — 진행 중 · 횟수 확인 뒤, 행을 넣기 직전이다
		DigestLedger pausing = new PausingLedger(this.ledger, () -> {
			requestPaused.countDown();
			try {
				roundFinished.await(2, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		});
		DigestRequestsService request = new DigestRequestsService(this.timezones, this.models, pausing, this.owners,
				generation(at), dispatch(), this.transaction, () -> at);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<DigestRequestOutcome> requested = pool.submit(() -> request.request(user));
			assertThat(requestPaused.await(20, TimeUnit.SECONDS)).isTrue();

			assertThat(scheduling.runRound()).isZero();
			roundFinished.countDown();

			assertThat(requested.get(30, TimeUnit.SECONDS))
				.isEqualTo(new DigestRequestOutcome.Requested(LocalDate.parse("2026-11-19"), false));
		}
		finally {
			roundFinished.countDown();
			pool.shutdownNow();
		}

		assertThat(rows(user)).extracting(row -> row.get("trigger")).containsExactly("MANUAL");
		assertThat(this.enqueued).hasSize(1);
	}

	/** 직전 끝을 읽기 전에 멈추는 장부 — 나머지는 그대로 넘긴다 */
	private record PausingLedger(DigestLedger ledger, Runnable pause) implements DigestLedger {

		@Override
		public Map<UserId, LocalDate> latestDates(java.util.Collection<UserId> users) {
			return this.ledger.latestDates(users);
		}

		@Override
		public java.util.Optional<LocalDate> latestDate(UserId user) {
			return this.ledger.latestDate(user);
		}

		@Override
		public java.util.Optional<Instant> lastCoveredEnd(UserId user) {
			this.pause.run();
			return this.ledger.lastCoveredEnd(user);
		}

		@Override
		public java.util.Optional<net.yozmnews.digest.domain.digest.DigestPeriod> latestPeriodOn(UserId user,
				LocalDate date) {
			return this.ledger.latestPeriodOn(user, date);
		}

		@Override
		public boolean inProgress(UserId user) {
			return this.ledger.inProgress(user);
		}

		@Override
		public int manualCountBetween(UserId user, Instant from, Instant to) {
			return this.ledger.manualCountBetween(user, from, to);
		}

		@Override
		public List<net.yozmnews.digest.domain.digest.DigestId> stalledScheduled(Instant before, int limit) {
			return this.ledger.stalledScheduled(before, limit);
		}

		@Override
		public List<net.yozmnews.digest.domain.digest.DigestId> stalledRunning(Instant before, int limit) {
			return this.ledger.stalledRunning(before, limit);
		}

	}

	/** 둘을 함께 시작해 결과를 모은다 */
	private List<DigestRequestOutcome> race(Callable<?> first, Callable<?> second) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<DigestRequestOutcome> outcomes = new ArrayList<>();
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (Callable<?> task : List.of(first, second)) {
				futures.add(pool.submit(() -> {
					start.await(20, TimeUnit.SECONDS);
					return task.call();
				}));
			}
			start.countDown();
			for (Future<?> future : futures) {
				if (future.get(30, TimeUnit.SECONDS) instanceof DigestRequestOutcome outcome) {
					outcomes.add(outcome);
				}
			}
		}
		finally {
			pool.shutdownNow();
		}
		return outcomes;
	}

	/** 쓰는 계정 — `America/Los_Angeles` · 03:17, OpenAI 키 하나(그 상태) */
	private UserId user(String email, String keyStatus) {
		UserId user = new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, weekly_report,
				    weekly_report_day, monthly_report, monthly_report_day, candidate_volume, big_slots, summary_length, reasoning_effort,
				    updated_at, version)
				values (:user, :tagSet, 'America/Los_Angeles', '03:17', true, 1, true, 1, 'NORMAL', 3, 'NORMAL', 'MEDIUM',
				    now(), 0)
				""").param("user", user.value()).param("tagSet", this.tagSets.activeTagSet().id().value()).update();
		this.jdbc.sql("""
				insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
				  validated_at, created_at)
				values (:user, 'openai', '\\x01'::bytea, '\\x01'::bytea, 1, '', :status, now(), now())
				""").param("user", user.value()).param("status", keyStatus).update();
		return user;
	}

	/** 끝난 수동 다이제스트 — 그 날짜 · 만든 시각 */
	private void manualRow(UserId user, String date, Instant createdAt) {
		this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, created_at,
				    dispatch_token, dispatched_at, started_at, completed_at, provider, model, version)
				values (:user, cast(:date as date), cast(:created as timestamptz) - interval '1 day', :created, 'EMPTY', 'MANUAL',
				    :created,
				    gen_random_uuid(), :created, :created, :created, 'openai', 'm', 0)
				""")
			.param("user", user.value())
			.param("date", date)
			.param("created", Timestamp.from(createdAt))
			.update();
	}

	private void finish(Map<String, Object> row, String status) {
		this.jdbc.sql("""
				update digests set status = :status, started_at = now(), completed_at = now(), provider = 'openai', model = 'm'
				where id = :id
				""").param("status", status).param("id", row.get("id")).update();
	}

	private List<Map<String, Object>> rows(UserId user) {
		return this.jdbc.sql("""
				select id, status, trigger, digest_date, period_start, period_end, dispatch_token from digests
				where user_id = :user order by id
				""").param("user", user.value()).query().listOfRows();
	}

}
