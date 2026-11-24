package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
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

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.DigestRounds;
import net.yozmnews.digest.domain.digest.DigestLedger;
import net.yozmnews.digest.domain.digest.DigestOwnerLock;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.DigestModelDirectory;
import net.yozmnews.user.DigestScheduleDirectory;
import net.yozmnews.user.UserTables;
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
 * 일정 회차 — 실제 PostgreSQL 과 실제 일정 목록 · 쓸 모델 조회로, 생성 시각이 된 사용자의 다이제스트 행을 하나 만들고 커밋 뒤 그 토큰으로 생성
 * 작업을 등록하는지 본다. 작업 등록은 포트의 기록 대체물이 받는다(외부 경계). 시계는 고정한다.
 * <p>
 * 이 시험의 사용자는 `America/Denver` · 03:17 이다 — 다른 시험이 남긴 기본 일정(`Asia/Seoul` 07:00)의 사용자는 이 시각에 창 밖이라 행이
 * 생기지 않는다. 기대값은 손으로 셈한 리터럴이다(2026-11-19 03:17 MST = 10:17 UTC).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class DigestSchedulingTests {

	/** 2026-11-19 03:17 MST(UTC−7) */
	private static final Instant T = Instant.parse("2026-11-19T10:17:00Z");

	@Autowired
	JobScheduler digestJobs;

	@Autowired
	DigestScheduleDirectory schedules;

	@Autowired
	DigestModelDirectory models;

	@Autowired
	DigestLedger ledger;

	@Autowired
	DigestOwnerLock owners;

	@Autowired
	DigestGeneration generation;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	/** 기록 대체물이 받은 등록 — (다이제스트 ID, 토큰) */
	private final List<Map.Entry<Long, UUID>> enqueued = Collections.synchronizedList(new ArrayList<>());

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
		this.enqueued.clear();
	}

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from digests").update();
		this.jdbc.sql("delete from llm_credentials where user_id in (select id from users where email like 'digest-schedule-round-%')")
			.update();
		UserTables.deleteByEmail(this.jdbc, "digest-schedule-round-%");
	}

	private DigestScheduling scheduling(Instant now) {
		DigestDispatch dispatch = new DigestDispatch((id, token) -> this.enqueued.add(Map.entry(id.value(), token)));
		return new DigestScheduling(this.schedules, this.models, this.ledger, this.owners, this.generation,
				dispatch, this.transaction, () -> now);
	}

	@Test
	void createsTheDueDigestOnceAndEnqueuesItsToken() {
		UserId user = user("digest-schedule-round-due@example.com", true);

		assertThat(scheduling(T.minusSeconds(60)).runRound()).isZero();
		assertThat(rows(user)).isEmpty();

		assertThat(scheduling(T.plusSeconds(60)).runRound()).isEqualTo(1);
		assertThat(scheduling(T.plusSeconds(120)).runRound()).isZero();

		List<Map<String, Object>> rows = rows(user);
		assertThat(rows).hasSize(1);
		Map<String, Object> row = rows.getFirst();
		assertThat(row).containsEntry("status", "SCHEDULED")
			.containsEntry("trigger", "SCHEDULED")
			.containsEntry("digest_date", java.sql.Date.valueOf("2026-11-19"))
			.containsEntry("period_start", Timestamp.from(Instant.parse("2026-11-18T10:17:00Z")))
			.containsEntry("period_end", Timestamp.from(T));
		assertThat(this.enqueued).containsExactly(Map.entry((Long) row.get("id"), (UUID) row.get("dispatch_token")));
	}

	/**
	 * 다음 기간의 시작은 끝난 다이제스트 가운데 {@code READY} · {@code EMPTY} 의 가장 늦은 기간 끝이다 — 계기와 무관하고, 그보다 늦게 끝난
	 * 실패 다이제스트의 기간은 다음 다이제스트가 이어서 덮는다.
	 */
	@Test
	void theNextPeriodStartsAtTheLastReadyOrEmptyEndOfAnyTrigger() {
		UserId user = user("digest-schedule-round-next@example.com", true);
		// 사흘 전 날짜의 일정 READY(끝 2026-11-16T10:17Z) · 이틀 전 날짜의 수동 EMPTY(끝 2026-11-17T10:17Z) · 전날의 일정 FAILED(끝 2026-11-18T10:17Z)
		ended(user, "2026-11-16", "2026-11-15T10:17:00Z", "2026-11-16T10:17:00Z", "READY", "SCHEDULED");
		ended(user, "2026-11-17", "2026-11-16T10:17:00Z", "2026-11-17T10:17:00Z", "EMPTY", "MANUAL");
		ended(user, "2026-11-18", "2026-11-17T10:17:00Z", "2026-11-18T10:17:00Z", "FAILED", "SCHEDULED");

		assertThat(scheduling(T.plusSeconds(60)).runRound()).isEqualTo(1);

		Map<String, Object> created = rows(user).getLast();
		assertThat(created).containsEntry("digest_date", java.sql.Date.valueOf("2026-11-19"))
			.containsEntry("period_start", Timestamp.from(Instant.parse("2026-11-17T10:17:00Z")))
			.containsEntry("period_end", Timestamp.from(T));
	}

	/** 끝난 다이제스트 행 하나 — 실패면 코드 {@code PROVIDER_UNAVAILABLE} */
	private void ended(UserId user, String date, String start, String end, String status, String trigger) {
		this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, error_code,
				    created_at, dispatch_token, dispatched_at, started_at, completed_at, version)
				values (:user, cast(:date as date), cast(:start as timestamptz), cast(:end as timestamptz), :status,
				    :trigger, case when :status = 'FAILED' then 'PROVIDER_UNAVAILABLE' end, now(), gen_random_uuid(),
				    now(), now(), now(), 0)
				""")
			.param("user", user.value())
			.param("date", date)
			.param("start", start)
			.param("end", end)
			.param("status", status)
			.param("trigger", trigger)
			.update();
	}

	@Test
	void usersWithoutAModelOrNotActiveOrPastTheWindowGetNothing() {
		user("digest-schedule-round-keyless@example.com", false);
		UserId suspended = user("digest-schedule-round-suspended@example.com", true);
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", suspended.value()).update();
		UserId late = user("digest-schedule-round-late@example.com", true);

		// 대조 — 창 안의 쓰는 계정 하나는 만든다
		assertThat(scheduling(T.plusSeconds(60)).runRound()).isEqualTo(1);
		// 늦은 사용자는 이미 행이 있으므로, 행을 지우고 창이 지난 시각에 다시 돈다
		this.jdbc.sql("delete from digests where user_id = :id").param("id", late.value()).update();
		assertThat(scheduling(T.plusSeconds(2 * 3600)).runRound()).isZero();

		assertThat(this.jdbc.sql("""
				select count(*) from digests d join users u on u.id = d.user_id
				where u.email like 'digest-schedule-round-%' and u.email <> 'digest-schedule-round-late@example.com'
				""").query(Long.class).single()).isZero();
		assertThat(this.enqueued).hasSize(1);
	}

	@Test
	void aManualDigestOfTheDateAlreadyMadeLeavesTheScheduleOut() {
		UserId user = user("digest-schedule-round-manual@example.com", true);
		this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, created_at,
				    dispatch_token, dispatched_at, started_at, completed_at, version)
				values (:user, date '2026-11-19', timestamptz '2026-11-18T09:00:00Z', timestamptz '2026-11-19T09:00:00Z',
				    'READY', 'MANUAL', now(), gen_random_uuid(), now(), now(), now(), 0)
				""").param("user", user.value()).update();

		assertThat(scheduling(T.plusSeconds(60)).runRound()).isZero();

		assertThat(rows(user)).extracting(row -> row.get("trigger")).containsExactly("MANUAL");
	}

	@Test
	void twoRoundsAtTheSameTimeMakeOneDigest() throws Exception {
		UserId user = user("digest-schedule-round-race@example.com", true);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> round = () -> {
			start.await(20, TimeUnit.SECONDS);
			return scheduling(T.plusSeconds(60)).runRound();
		};
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Integer> first = pool.submit(round);
			Future<Integer> second = pool.submit(round);
			start.countDown();

			assertThat(first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS)).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(rows(user)).hasSize(1);
		assertThat(this.enqueued).hasSize(1);
	}

	/** 쓰는 계정 — `America/Denver` · 03:17, 키가 있으면 OpenAI 유효 키 */
	private UserId user(String email, boolean withKey) {
		UserId user = new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, weekly_report,
				    weekly_report_day, monthly_report, candidate_volume, big_slots, summary_length, reasoning_effort,
				    updated_at, version)
				values (:user, :tagSet, 'America/Denver', '03:17', true, 1, true, 'NORMAL', 3, 'NORMAL', 'MEDIUM',
				    now(), 0)
				""").param("user", user.value()).param("tagSet", this.tagSets.activeTagSet().id().value()).update();
		if (withKey) {
			this.jdbc.sql("""
					insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
					  validated_at, created_at)
					values (:user, 'openai', '\\x01'::bytea, '\\x01'::bytea, 1, '', 'VALID', now(), now())
					""").param("user", user.value()).update();
		}
		return user;
	}

	private List<Map<String, Object>> rows(UserId user) {
		return this.jdbc.sql("""
				select id, status, trigger, digest_date, period_start, period_end, dispatch_token from digests
				where user_id = :user order by id
				""").param("user", user.value()).query().listOfRows();
	}

}
