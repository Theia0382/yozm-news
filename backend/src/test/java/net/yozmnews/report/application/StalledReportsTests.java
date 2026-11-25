package net.yozmnews.report.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.yozmnews.TestClocks;
import net.yozmnews.TestClocks.MovableClock;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.AppClock;
import net.yozmnews.report.ReportRounds;
import net.yozmnews.report.domain.report.ReportLedger;
import net.yozmnews.report.domain.report.ReportRepository;
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
 * 거두기 회차 — 등록 뒤 15분 지나도 시작되지 않은 리포트는 새 토큰으로 다시 등록하고, 예약 시각에서 1시간이 지났으면 시작하지 않은 채 중단
 * 실패로 끝내며(시간대를 적는다), 시작한 지 40분이 지나도 생성 중인 리포트는 중단 실패로 끝낸다. 지난 시간은 시계가 넘긴 시간을 뺀 경과다. 실제
 * PostgreSQL, 등록은 기록 대체물, 시계는 고정한다. 행의 사용자({@link #NO_ACCOUNT})는 계정이 없어 시간대는 UTC 다 — 계정 ID 순번이 닿지 않는
 * 큰 값이라 다른 시험이 남긴 계정과 겹치지 않는다.
 * <p>
 * 지키는 규칙: report-R1 · ADR-0232 와 같은 꼴
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class StalledReportsTests {

	private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

	/** 계정이 없는 사용자 */
	private static final long NO_ACCOUNT = 9_000_000_042L;

	@Autowired
	JobScheduler jobs;

	@Autowired
	ReportRepository reports;

	@Autowired
	ReportLedger ledger;

	@Autowired
	UserTimezones timezones;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private final List<Map.Entry<Long, UUID>> enqueued = Collections.synchronizedList(new ArrayList<>());

	/** 행마다 다른 기간 — 같은 사용자 · 종류 · 기간은 하나다 */
	private int rows;

	@BeforeEach
	void stopReportRounds() {
		ReportRounds.stop(this.jobs);
		this.enqueued.clear();
	}

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from reports").update();
	}

	private StalledReports stalled(Instant now) {
		return stalled(TestClocks.fixed(now));
	}

	private StalledReports stalled(AppClock clock) {
		ReportDispatch dispatch = new ReportDispatch((id, token) -> this.enqueued.add(Map.entry(id.value(), token)));
		return new StalledReports(this.reports, this.ledger, new ReportEndings(this.reports), dispatch, this.timezones,
				this.transaction, clock);
	}

	@Test
	void aReportNotStartedFifteenMinutesAfterItsDispatchIsDispatchedAgainWithANewToken() {
		UUID old = UUID.fromString("5b1e7c3a-2d4f-4e8b-9a61-0c3d7f2e4b18");
		long stale = scheduled(NOW.minusSeconds(16 * 60), NOW.minusSeconds(16 * 60), old);
		long fresh = scheduled(NOW.minusSeconds(14 * 60), NOW.minusSeconds(14 * 60), UUID.randomUUID());

		stalled(NOW).runRound();

		Map<String, Object> again = row(stale);
		assertThat(again).containsEntry("status", "SCHEDULED")
			.containsEntry("dispatched_at", Timestamp.from(NOW))
			.containsEntry("scheduled_at", Timestamp.from(NOW.minusSeconds(16 * 60)));
		assertThat(again.get("dispatch_token")).isNotEqualTo(old);
		assertThat(this.enqueued).containsExactly(Map.entry(stale, (UUID) again.get("dispatch_token")));
		// 대조 — 14분 된 등록은 그대로
		assertThat(row(fresh)).containsEntry("dispatched_at", Timestamp.from(NOW.minusSeconds(14 * 60)));
	}

	@Test
	void aReportNotStartedAnHourAfterItWasScheduledEndsInterruptedWithoutStartingAndWithAZone() {
		long late = scheduled(NOW.minusSeconds(61 * 60), NOW.minusSeconds(16 * 60), UUID.randomUUID());
		long early = scheduled(NOW.minusSeconds(59 * 60), NOW.minusSeconds(16 * 60), UUID.randomUUID());

		stalled(NOW).runRound();

		assertThat(row(late)).containsEntry("status", "FAILED")
			.containsEntry("error_code", "INTERRUPTED")
			.containsEntry("error_message", "시작 기한 안에 생성이 시작되지 않았다")
			.containsEntry("completed_at", Timestamp.from(NOW))
			.containsEntry("zone", "UTC")
			.containsEntry("started_at", null);
		// 대조 — 예약한 지 59분이면 다시 등록한다
		assertThat(row(early)).containsEntry("status", "SCHEDULED");
		assertThat(this.enqueued).extracting(Map.Entry::getKey).containsExactly(early);
	}

	@Test
	void theStartDeadlineCountsFromTheScheduledTimeNotFromWhenTheRowWasMade() {
		// 사흘 전에 만들었지만 16분 전에 다시 만들기를 받은 행 — 예약 시각으로 재므로 다시 등록한다
		long regenerated = scheduled(NOW.minusSeconds(16 * 60), NOW.minusSeconds(16 * 60), UUID.randomUUID());
		this.jdbc.sql("update reports set created_at = :made where id = :id")
			.param("made", Timestamp.from(NOW.minus(Duration.ofDays(3))))
			.param("id", regenerated)
			.update();

		stalled(NOW).runRound();

		assertThat(row(regenerated)).containsEntry("status", "SCHEDULED").containsEntry("dispatched_at", Timestamp.from(NOW));
		assertThat(this.enqueued).extracting(Map.Entry::getKey).containsExactly(regenerated);
	}

	@Test
	void aReportRunningFortyMinutesEndsInterruptedAndKeepsItsModel() {
		long stuck = running(NOW.minusSeconds(41 * 60));
		long busy = running(NOW.minusSeconds(39 * 60));

		stalled(NOW).runRound();

		assertThat(row(stuck)).containsEntry("status", "FAILED")
			.containsEntry("error_code", "INTERRUPTED")
			.containsEntry("error_message", "임대 안에 생성이 끝나지 않았다")
			.containsEntry("provider", "openai")
			.containsEntry("model", "gpt-5.5-mini")
			.containsEntry("zone", "Asia/Seoul");
		assertThat(row(stuck).get("duration_ms")).isNull();
		// 대조 — 39분이면 그대로 생성 중
		assertThat(row(busy)).containsEntry("status", "RUNNING");
		assertThat(this.enqueued).isEmpty();
	}

	/**
	 * 측정용 시계를 넘겨도 진행 중인 리포트는 멈춘 것으로 보지 않는다 — 기다림 · 시작 기한 · 생성 임대는 시계가 넘긴 시간을 뺀 경과로 잰다. 둘 다
	 * NOW 1분 전에 등록 · 시작했다.
	 */
	@Test
	void aJumpOfTheClockDoesNotStallReportsInProgress() {
		Instant before = NOW.minusSeconds(60);
		long waiting = scheduled(before, before, UUID.randomUUID());
		long busy = running(before);
		MovableClock clock = TestClocks.movable(NOW).jump(Duration.ofHours(24));

		stalled(clock).runRound();

		assertThat(row(waiting)).containsEntry("status", "SCHEDULED").containsEntry("dispatched_at", Timestamp.from(before));
		assertThat(row(busy)).containsEntry("status", "RUNNING");
		assertThat(this.enqueued).isEmpty();

		// 대조 — 넘긴 뒤 실제로 40분이 흐르면 경과 41분이라 생성 임대가 지나 끝내고, 생성 대기는 다시 등록한다(예약한 지 41분)
		clock.advance(Duration.ofMinutes(40));
		stalled(clock).runRound();

		assertThat(row(busy)).containsEntry("status", "FAILED").containsEntry("error_code", "INTERRUPTED");
		assertThat(row(waiting)).containsEntry("status", "SCHEDULED")
			.containsEntry("dispatched_at", Timestamp.from(Instant.parse("2026-11-20T12:40:00Z")));
		assertThat(this.enqueued).extracting(Map.Entry::getKey).containsExactly(waiting);
	}

	@Test
	void finishedReportsAreLeftAlone() {
		long done = running(NOW.minusSeconds(3 * 3600));
		this.jdbc.sql("update reports set status = 'EMPTY', completed_at = :at where id = :id")
			.param("at", Timestamp.from(NOW.minusSeconds(3 * 3600)))
			.param("id", done)
			.update();

		stalled(NOW).runRound();

		assertThat(row(done)).containsEntry("status", "EMPTY");
		assertThat(this.enqueued).isEmpty();
	}

	/** 생성을 기다리는 주간 리포트 — 계정이 없는 사용자, 예약 · 만든 시각 {@code scheduledAt}, 등록 시각 {@code dispatchedAt} */
	private long scheduled(Instant scheduledAt, Instant dispatchedAt, UUID token) {
		Instant start = Instant.parse("2026-11-09T00:00:00Z").minus(Duration.ofDays(7L * this.rows++));
		return this.jdbc.sql("""
				insert into reports (user_id, period_kind, period_start, period_end, status, created_at, dispatch_token,
				                     dispatched_at, scheduled_at, regenerations, version)
				values (:user, 'WEEK', :start, :end, 'SCHEDULED', :scheduled, :token, :dispatched, :scheduled, 0, 0)
				returning id""")
			.param("user", NO_ACCOUNT)
			.param("start", Timestamp.from(start))
			.param("end", Timestamp.from(start.plus(Duration.ofDays(7))))
			.param("scheduled", Timestamp.from(scheduledAt))
			.param("dispatched", Timestamp.from(dispatchedAt))
			.param("token", token)
			.query(Long.class)
			.single();
	}

	/** 생성 중인 주간 리포트 — 계정이 없는 사용자, {@code startedAt} 에 시작(OpenAI · 서울) */
	private long running(Instant startedAt) {
		long id = scheduled(startedAt, startedAt, UUID.randomUUID());
		this.jdbc.sql("""
				update reports set status = 'RUNNING', provider = 'openai', model = 'gpt-5.5-mini', started_at = :at,
				  stats_snapshot = cast('{}' as jsonb), zone = 'Asia/Seoul'
				where id = :id""").param("at", Timestamp.from(startedAt)).param("id", id).update();
		return id;
	}

	private Map<String, Object> row(long id) {
		return this.jdbc.sql("""
				select status, error_code, error_message, completed_at, dispatched_at, scheduled_at, dispatch_token, zone,
				       started_at, provider, model, duration_ms
				from reports where id = :id""").param("id", id).query().singleRow();
	}

}
