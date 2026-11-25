package net.yozmnews.report.application;

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
import net.yozmnews.digest.ReadyDigestDays;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportRounds;
import net.yozmnews.report.domain.report.ReportLedger;
import net.yozmnews.report.domain.report.ReportOwnerLock;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.ReportModelDirectory;
import net.yozmnews.user.ReportScheduleDirectory;
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
 * 일정 회차 — 실제 PostgreSQL 과 실제 일정 목록 · 쓸 모델 · 다이제스트 날짜 수 조회로, 리포트 시점이 된 사용자의 주간 · 월간 리포트 행을 하나
 * 만들고 커밋 뒤 그 토큰으로 생성 작업을 등록하는지 본다. 작업 등록은 포트의 기록 대체물이 받는다(외부 경계). 시계는 고정한다.
 * <p>
 * 이 시험의 사용자는 `America/Denver` · 03:17 · 주간 목요일 · 월간 19일이다 — 다른 시험이 남긴 기본 일정(`Asia/Seoul` 07:00 · 월요일 · 1일)의
 * 사용자는 이 시각에 창 밖이다. 기대값은 손으로 셈한 리터럴이다(2026-11-19(목) 03:17 MST = 10:17 UTC, 10-19 03:17 MDT = 09:17 UTC).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReportSchedulingTests {

	/** 2026-11-19(목) 03:17 MST(UTC−7) — 주간 · 월간의 경계 */
	private static final Instant T = Instant.parse("2026-11-19T10:17:00Z");

	/** 주간 기간의 시작 — 2026-11-12(목) 03:17 MST */
	private static final Instant WEEK_START = Instant.parse("2026-11-12T10:17:00Z");

	/** 월간 기간의 시작 — 2026-10-19 03:17 MDT(UTC−6) */
	private static final Instant MONTH_START = Instant.parse("2026-10-19T09:17:00Z");

	@Autowired
	JobScheduler jobs;

	@Autowired
	ReportScheduleDirectory schedules;

	@Autowired
	ReportModelDirectory models;

	@Autowired
	ReadyDigestDays digestDays;

	@Autowired
	ReportLedger ledger;

	@Autowired
	ReportOwnerLock owners;

	@Autowired
	ReportGeneration generation;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	/** 기록 대체물이 받은 등록 — (리포트 ID, 토큰) */
	private final List<Map.Entry<Long, UUID>> enqueued = Collections.synchronizedList(new ArrayList<>());

	@BeforeEach
	void stopReportRounds() {
		ReportRounds.stop(this.jobs);
		this.enqueued.clear();
	}

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from reports").update();
		this.jdbc.sql("delete from digests where user_id in (select id from users where email like 'report-round-%')")
			.update();
		this.jdbc.sql("delete from llm_credentials where user_id in (select id from users where email like 'report-round-%')")
			.update();
		UserTables.deleteByEmail(this.jdbc, "report-round-%");
	}

	private ReportScheduling scheduling(Instant now) {
		ReportDispatch dispatch = new ReportDispatch((id, token) -> this.enqueued.add(Map.entry(id.value(), token)));
		return new ReportScheduling(this.schedules, this.models, this.digestDays, this.ledger, this.owners,
				this.generation, dispatch, this.transaction, () -> now);
	}

	@Test
	void createsTheDueWeeklyReportOnceAndEnqueuesItsToken() {
		UserId user = user("report-round-weekly@example.com", true, true, false);
		digestDays(user, 3);

		assertThat(scheduling(T.minusSeconds(60)).runRound()).isZero();
		assertThat(rows(user)).isEmpty();
		assertThat(scheduling(T.plusSeconds(60)).runRound()).isEqualTo(1);
		assertThat(scheduling(T.plusSeconds(600)).runRound()).isZero();

		List<Map<String, Object>> rows = rows(user);
		assertThat(rows).hasSize(1);
		Map<String, Object> row = rows.getFirst();
		assertThat(row).containsEntry("status", "SCHEDULED")
			.containsEntry("period_kind", "WEEK")
			.containsEntry("period_start", Timestamp.from(WEEK_START))
			.containsEntry("period_end", Timestamp.from(T));
		assertThat(this.enqueued).containsExactly(Map.entry((Long) row.get("id"), (UUID) row.get("dispatch_token")));
	}

	@Test
	void aWeekWithFewerThanThreeReadyDigestDaysGetsNoReport() {
		UserId user = user("report-round-few@example.com", true, true, false);
		// READY 행 셋이지만 날짜는 둘 — 같은 날짜를 수동으로 다시 만들었다
		digestDays(user, 2);
		digest(user, "2026-11-13", "2026-11-13T15:00:00Z");

		assertThat(scheduling(T.plusSeconds(60)).runRound()).isZero();
		assertThat(rows(user)).isEmpty();

		// 대조 — 셋째 날짜가 생기면 만든다
		digest(user, "2026-11-18", "2026-11-18T10:17:00Z");
		assertThat(scheduling(T.plusSeconds(120)).runRound()).isEqualTo(1);
	}

	@Test
	void aUserWithoutAUsableModelOrOutsideTheWindowGetsNoReport() {
		UserId keyless = user("report-round-keyless@example.com", false, true, false);
		digestDays(keyless, 3);
		UserId late = user("report-round-late@example.com", true, true, false);
		digestDays(late, 3);

		assertThat(scheduling(T.plusSeconds(2 * 3600)).runRound()).isZero();
		assertThat(rows(late)).isEmpty();
		assertThat(scheduling(T.plusSeconds(2 * 3600 - 1)).runRound()).isEqualTo(1);
		assertThat(rows(late)).hasSize(1);
		assertThat(rows(keyless)).isEmpty();
	}

	@Test
	void aMonthlyReportNeedsTwoReadyWeekliesWhollyInsideAndWaitsForUnfinishedOnes() {
		UserId user = user("report-round-monthly@example.com", true, true, true);
		weekly(user, "2026-10-22T09:17:00Z", "2026-10-29T09:17:00Z", "READY");
		long pending = weekly(user, "2026-10-29T09:17:00Z", "2026-11-05T10:17:00Z", "SCHEDULED");

		assertThat(scheduling(T.plusSeconds(60)).runRound()).isZero();
		assertThat(monthlyRows(user)).isEmpty();

		this.jdbc.sql("""
				update reports set status = 'READY', provider = 'openai', model = 'gpt-5.5-mini', started_at = now(),
				  completed_at = now(), stats_snapshot = cast('{}' as jsonb), zone = 'America/Denver',
				  input_tokens = 1, output_tokens = 1
				where id = :id""").param("id", pending).update();
		assertThat(scheduling(T.plusSeconds(660)).runRound()).isEqualTo(1);

		List<Map<String, Object>> rows = monthlyRows(user);
		assertThat(rows).hasSize(1);
		assertThat(rows.getFirst()).containsEntry("period_start", Timestamp.from(MONTH_START))
			.containsEntry("period_end", Timestamp.from(T));
	}

	@Test
	void aMonthlyReportIsNotMadeWithOneReadyWeeklyOrWithWeeklyReportsOff() {
		UserId one = user("report-round-one@example.com", true, true, true);
		weekly(one, "2026-10-22T09:17:00Z", "2026-10-29T09:17:00Z", "READY");
		weekly(one, "2026-10-29T09:17:00Z", "2026-11-05T10:17:00Z", "FAILED");
		// 월간 기간(10-19 ~ 11-19)의 앞 · 뒤에 걸친 READY 주간은 세지 않는다 — 세면 READY 셋이라 만든다
		weekly(one, "2026-10-15T09:17:00Z", "2026-10-22T09:17:00Z", "READY");
		weekly(one, "2026-11-15T10:17:00Z", "2026-11-22T10:17:00Z", "READY");
		UserId off = user("report-round-off@example.com", true, false, true);
		weekly(off, "2026-10-22T09:17:00Z", "2026-10-29T09:17:00Z", "READY");
		weekly(off, "2026-10-29T09:17:00Z", "2026-11-05T10:17:00Z", "READY");

		assertThat(scheduling(T.plusSeconds(60)).runRound()).isZero();
		assertThat(monthlyRows(one)).isEmpty();
		assertThat(monthlyRows(off)).isEmpty();
	}

	@Test
	void twoRoundsAtTheSameTimeMakeOneReport() throws Exception {
		UserId user = user("report-round-race@example.com", true, true, false);
		digestDays(user, 3);
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

	/** 쓰는 계정 — `America/Denver` · 03:17 · 주간 목요일 · 월간 19일, 키가 있으면 OpenAI 유효 키 */
	private UserId user(String email, boolean withKey, boolean weekly, boolean monthly) {
		UserId user = new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, weekly_report,
				    weekly_report_day, monthly_report, monthly_report_day, candidate_volume, big_slots, summary_length,
				    reasoning_effort, updated_at, version)
				values (:user, :tagSet, 'America/Denver', '03:17', :weekly, 4, :monthly, 19, 'NORMAL', 3, 'NORMAL', 'MEDIUM',
				    now(), 0)
				""")
			.param("user", user.value())
			.param("tagSet", this.tagSets.activeTagSet().id().value())
			.param("weekly", weekly)
			.param("monthly", monthly)
			.update();
		if (withKey) {
			this.jdbc.sql("""
					insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
					  validated_at, created_at)
					values (:user, 'openai', '\\x01'::bytea, '\\x01'::bytea, 1, '', 'VALID', now(), now())
					""").param("user", user.value()).update();
		}
		return user;
	}

	/** 주간 기간 안 날짜마다 READY 다이제스트 — 11-12 부터 {@code days} 날, 기간 끝은 그날 03:17 MST */
	private void digestDays(UserId user, int days) {
		for (int day = 0; day < days; day++) {
			digest(user, "2026-11-" + (12 + day), "2026-11-" + (12 + day) + "T10:17:00Z");
		}
	}

	private void digest(UserId user, String date, String periodEnd) {
		this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, created_at,
				    dispatch_token, dispatched_at, started_at, completed_at, version)
				values (:user, cast(:date as date), cast(:end as timestamptz) - interval '1 day', cast(:end as timestamptz),
				    'READY', 'MANUAL', cast(:end as timestamptz), gen_random_uuid(), cast(:end as timestamptz),
				    cast(:end as timestamptz), cast(:end as timestamptz), 0)
				""").param("user", user.value()).param("date", date).param("end", periodEnd).update();
	}

	/** 주간 리포트 행 — 끝난 행은 시작 · 스냅숏 · 시간대를 함께(테이블 제약) */
	private long weekly(UserId user, String start, String end, String status) {
		boolean started = !status.equals("SCHEDULED");
		boolean finished = !status.equals("SCHEDULED");
		return this.jdbc.sql("""
				insert into reports (user_id, period_kind, period_start, period_end, status, provider, model, input_tokens,
				                     output_tokens, stats_snapshot, error_code, created_at, started_at, completed_at, zone,
				                     dispatch_token, dispatched_at, scheduled_at, regenerations, version)
				values (:user, 'WEEK', cast(:start as timestamptz), cast(:end as timestamptz), :status, :provider, :model,
				        :tokens, :tokens, cast(:snapshot as jsonb), :error, cast(:end as timestamptz), :started, :completed,
				        :zone, gen_random_uuid(), cast(:end as timestamptz), cast(:end as timestamptz), 0, 1)
				returning id""")
			.param("user", user.value())
			.param("start", start)
			.param("end", end)
			.param("status", status)
			.param("provider", started ? "openai" : null, java.sql.Types.VARCHAR)
			.param("model", started ? "gpt-5.5-mini" : null, java.sql.Types.VARCHAR)
			.param("tokens", status.equals("READY") ? 1 : null, java.sql.Types.INTEGER)
			.param("snapshot", started ? "{}" : null, java.sql.Types.VARCHAR)
			.param("error", status.equals("FAILED") ? "PROVIDER_UNAVAILABLE" : null, java.sql.Types.VARCHAR)
			.param("started", started ? Timestamp.from(Instant.parse(end)) : null, java.sql.Types.TIMESTAMP)
			.param("completed", finished ? Timestamp.from(Instant.parse(end)) : null, java.sql.Types.TIMESTAMP)
			.param("zone", started ? "America/Denver" : null, java.sql.Types.VARCHAR)
			.query(Long.class)
			.single();
	}

	private List<Map<String, Object>> rows(UserId user) {
		return this.jdbc.sql("""
				select id, status, period_kind, period_start, period_end, dispatch_token from reports
				where user_id = :user and period_kind = 'WEEK' and period_end = timestamptz '2026-11-19 10:17Z' order by id
				""").param("user", user.value()).query().listOfRows();
	}

	private List<Map<String, Object>> monthlyRows(UserId user) {
		return this.jdbc.sql("""
				select id, status, period_start, period_end from reports where user_id = :user and period_kind = 'MONTH'
				order by id
				""").param("user", user.value()).query().listOfRows();
	}

}
