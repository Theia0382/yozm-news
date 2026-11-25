package net.yozmnews.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.yozmnews.TestClocks;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportRegenerationOutcome;
import net.yozmnews.report.ReportRounds;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.domain.report.ReportLedger;
import net.yozmnews.report.domain.report.ReportRepository;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.ReportFailed;
import net.yozmnews.user.ReportModelDirectory;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리포트 다시 만들기 — 그 사용자의 실패한 리포트만, 리포트마다 세 번까지, 쓸 모델이 있을 때 같은 행을 생성을 기다림으로 되돌리고(시작 · 결과 ·
 * 시작의 기록을 비움) 커밋 뒤 새 토큰으로 등록한다. 되돌린 행의 시작 기한은 예약 시각으로 잰다. 다시 만든 리포트가 또 실패하면 같은 리포트의
 * 실패가 실패 시각이 다르게 한 번 더 난다. 실제 PostgreSQL, 등록은 기록 대체물, 시계는 고정한다.
 * <p>
 * 지키는 규칙: report-R1 · report-R32
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@RecordApplicationEvents
class ReportRequestsServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");

	@Autowired
	JobScheduler jobs;

	@Autowired
	ReportRepository reports;

	@Autowired
	ReportLedger ledger;

	@Autowired
	ReportModelDirectory models;

	@Autowired
	UserTimezones timezones;

	@Autowired
	ReportEndings endings;

	@Autowired
	ReportRun run;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	private final List<Map.Entry<Long, UUID>> enqueued = Collections.synchronizedList(new ArrayList<>());

	/** 이 시험에서 넣은 실패 리포트 수 — 넣을 때마다 다음 주 기간 */
	private int failedRows;

	@BeforeEach
	void stopReportRounds() {
		ReportRounds.stop(this.jobs);
		this.enqueued.clear();
	}

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from reports").update();
		this.jdbc.sql("delete from llm_credentials where user_id in (select id from users where email like 'report-again-%')")
			.update();
		UserTables.deleteByEmail(this.jdbc, "report-again-%");
	}

	private ReportRequestsService requests(Instant now) {
		ReportDispatch dispatch = new ReportDispatch((id, token) -> this.enqueued.add(Map.entry(id.value(), token)));
		return new ReportRequestsService(this.reports, this.models, dispatch, this.transaction, () -> now);
	}

	@Test
	void aFailedReportIsScheduledAgainOnTheSameRowAndDispatchedAfterCommit() {
		UserId user = user("report-again-failed@example.com", true);
		long id = failed(user, 0, NOW.minus(Duration.ofDays(3)));

		assertThat(requests(NOW).regenerate(user, new ReportId(id)))
			.contains(new ReportRegenerationOutcome.Requested());

		Map<String, Object> row = row(id);
		assertThat(row).containsEntry("status", "SCHEDULED")
			.containsEntry("regenerations", 1)
			.containsEntry("scheduled_at", Timestamp.from(NOW))
			.containsEntry("dispatched_at", Timestamp.from(NOW))
			.containsEntry("created_at", Timestamp.from(NOW.minus(Duration.ofDays(3))))
			.containsEntry("started_at", null)
			.containsEntry("completed_at", null)
			.containsEntry("provider", null)
			.containsEntry("error_code", null)
			.containsEntry("stats_snapshot", null)
			.containsEntry("zone", null)
			.containsEntry("previous_report_id", null);
		assertThat(this.enqueued).containsExactly(Map.entry(id, (UUID) row.get("dispatch_token")));
	}

	@Test
	void onlyAFailedReportWithRegenerationsLeftAndAUsableModelIsScheduledAgain() {
		UserId user = user("report-again-refused@example.com", true);
		long ready = finished(user, "READY", 1);
		long spent = failed(user, 3, NOW.minus(Duration.ofDays(1)));
		UserId keyless = user("report-again-keyless@example.com", false);
		long noModel = failed(keyless, 0, NOW.minus(Duration.ofDays(1)));

		assertThat(requests(NOW).regenerate(user, new ReportId(ready)))
			.contains(new ReportRegenerationOutcome.NotFailed(ReportStatus.READY));
		assertThat(requests(NOW).regenerate(user, new ReportId(spent)))
			.contains(new ReportRegenerationOutcome.LimitReached());
		assertThat(requests(NOW).regenerate(keyless, new ReportId(noModel)))
			.contains(new ReportRegenerationOutcome.NoUsableModel());
		// 다른 사용자의 리포트 · 없는 리포트는 없는 것이다
		assertThat(requests(NOW).regenerate(keyless, new ReportId(spent))).isEmpty();
		assertThat(requests(NOW).regenerate(user, new ReportId(Long.MAX_VALUE))).isEmpty();

		assertThat(row(ready)).containsEntry("status", "READY");
		assertThat(row(spent)).containsEntry("status", "FAILED").containsEntry("regenerations", 3);
		assertThat(row(noModel)).containsEntry("status", "FAILED").containsEntry("regenerations", 0);
		assertThat(this.enqueued).isEmpty();
		// 대조 — 두 번 다시 만든 실패는 한 번 더 받는다
		long twice = failed(user, 2, NOW.minus(Duration.ofDays(1)));
		assertThat(requests(NOW).regenerate(user, new ReportId(twice)))
			.contains(new ReportRegenerationOutcome.Requested());
		assertThat(row(twice)).containsEntry("regenerations", 3);
	}

	@Test
	void refusesACallInsideATransaction() {
		UserId user = user("report-again-transaction@example.com", true);
		long id = failed(user, 0, NOW);

		assertThatIllegalStateException().isThrownBy(() -> this.transaction
			.executeWithoutResult(status -> requests(NOW).regenerate(user, new ReportId(id))))
			.withMessage(ReportRequestsService.IN_TRANSACTION);
		assertThat(row(id)).containsEntry("status", "FAILED");
	}

	@Test
	void aRegeneratedOldReportIsDispatchedAgainAfterFifteenMinutesAndEndsAnHourAfterItWasScheduled() {
		UserId user = user("report-again-stalled@example.com", true);
		long id = failed(user, 0, NOW.minus(Duration.ofDays(3)));
		requests(NOW).regenerate(user, new ReportId(id));

		// 만든 지 사흘이지만 예약은 16분 전 — 다시 등록한다
		stalled(NOW.plus(Duration.ofMinutes(16))).runRound();
		assertThat(row(id)).containsEntry("status", "SCHEDULED")
			.containsEntry("dispatched_at", Timestamp.from(NOW.plus(Duration.ofMinutes(16))));
		// 예약에서 1시간 1분 — 시작하지 않은 채 끝낸다
		stalled(NOW.plus(Duration.ofMinutes(61))).runRound();
		assertThat(row(id)).containsEntry("status", "FAILED").containsEntry("error_code", "INTERRUPTED");
	}

	@Test
	void aRegeneratedReportThatFailsAgainPublishesAnotherFailureWithAnotherTime() {
		UserId user = user("report-again-twice@example.com", true);
		long id = failed(user, 0, NOW.minus(Duration.ofDays(1)));

		requests(NOW).regenerate(user, new ReportId(id));
		key(user, false);
		this.run.run(new ReportId(id));
		key(user, true);
		requests(NOW.plusSeconds(60)).regenerate(user, new ReportId(id));
		key(user, false);
		this.run.run(new ReportId(id));

		List<ReportFailed> failures = this.events.stream(ReportFailed.class).toList();
		assertThat(failures).hasSize(2).allSatisfy(failure -> assertThat(failure.reportId()).isEqualTo(id));
		assertThat(failures.get(0).failedAt()).isNotEqualTo(failures.get(1).failedAt());
		assertThat(row(id)).containsEntry("regenerations", 2).containsEntry("error_code", "NO_USABLE_MODEL");
	}

	private StalledReports stalled(Instant now) {
		ReportDispatch dispatch = new ReportDispatch((reportId, token) -> this.enqueued.add(Map.entry(reportId.value(), token)));
		return new StalledReports(this.reports, this.ledger, this.endings, dispatch, this.timezones, this.transaction,
				TestClocks.fixed(now));
	}

	/**
	 * 시작한 뒤 실패한 주간 리포트 — 다시 만든 횟수 {@code regenerations}, 만든 시각 {@code made}, 직전 리포트를 가리킨다. 부를 때마다 다음 주
	 * 기간이다(같은 사용자 · 종류 · 기간은 하나)
	 */
	private long failed(UserId user, int regenerations, Instant made) {
		long previous = finished(user, "READY", 2);
		Instant start = Instant.parse("2026-11-16T00:00:00Z").plus(Duration.ofDays(7L * this.failedRows++));
		long id = this.jdbc.sql("""
				insert into reports (user_id, period_kind, period_start, period_end, status, provider, model, stats_snapshot,
				                     error_code, error_message, created_at, started_at, completed_at, zone, previous_report_id,
				                     dispatch_token, dispatched_at, scheduled_at, regenerations, version)
				values (:user, 'WEEK', :start, :end, 'FAILED', 'openai',
				        'gpt-5.5-mini', cast('{"version":1}' as jsonb), 'PROVIDER_UNAVAILABLE', '일시 장애', :made, :made, :made,
				        'Asia/Seoul', :previous, gen_random_uuid(), :made, :made, :regenerations, 1)
				returning id""")
			.param("user", user.value())
			.param("made", Timestamp.from(made))
			.param("start", Timestamp.from(start))
			.param("end", Timestamp.from(start.plus(Duration.ofDays(7))))
			.param("previous", previous)
			.param("regenerations", regenerations)
			.query(Long.class)
			.single();
		return id;
	}

	/** 끝난 주간 리포트 — 부를 때마다 앞선 주({@code weeksBefore} 주 전) */
	private long finished(UserId user, String status, int weeksBefore) {
		Instant start = Instant.parse("2026-11-16T00:00:00Z").minus(Duration.ofDays(7L * weeksBefore));
		return this.jdbc.sql("""
				insert into reports (user_id, period_kind, period_start, period_end, status, provider, model, input_tokens,
				                     output_tokens, stats_snapshot, created_at, started_at, completed_at, zone, dispatch_token,
				                     dispatched_at, scheduled_at, regenerations, version)
				values (:user, 'WEEK', :start, :end, :status, 'openai', 'gpt-5.5-mini', 1, 1, cast('{}' as jsonb), :end, :end,
				        :end, 'Asia/Seoul', gen_random_uuid(), :end, :end, 0, 1)
				on conflict (user_id, period_kind, period_start, period_end) do update set status = excluded.status
				returning id""")
			.param("user", user.value())
			.param("start", Timestamp.from(start))
			.param("end", Timestamp.from(start.plus(Duration.ofDays(7))))
			.param("status", status)
			.query(Long.class)
			.single();
	}

	/** 쓰는 계정 — 키가 있으면 OpenAI 유효 키 */
	private UserId user(String email, boolean withKey) {
		UserId user = new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, weekly_report,
				    weekly_report_day, monthly_report, monthly_report_day, candidate_volume, big_slots, summary_length,
				    reasoning_effort, updated_at, version)
				values (:user, :tagSet, 'Asia/Seoul', '07:00', true, 1, true, 1, 'NORMAL', 3, 'NORMAL', 'MEDIUM', now(), 0)
				""").param("user", user.value()).param("tagSet", this.tagSets.activeTagSet().id().value()).update();
		key(user, withKey);
		return user;
	}

	private void key(UserId user, boolean present) {
		this.jdbc.sql("delete from llm_credentials where user_id = :user").param("user", user.value()).update();
		if (present) {
			this.jdbc.sql("""
					insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version, key_hint, status,
					  validated_at, created_at)
					values (:user, 'openai', '\\x01'::bytea, '\\x01'::bytea, 1, '', 'VALID', now(), now())
					""").param("user", user.value()).update();
		}
	}

	private Map<String, Object> row(long id) {
		return this.jdbc.sql("""
				select status, regenerations, scheduled_at, dispatched_at, created_at, started_at, completed_at, provider,
				       error_code, stats_snapshot::text as stats_snapshot, zone, previous_report_id, dispatch_token
				from reports where id = :id""").param("id", id).query().singleRow();
	}

}
