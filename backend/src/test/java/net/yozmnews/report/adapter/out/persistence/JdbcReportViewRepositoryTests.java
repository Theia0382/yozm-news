package net.yozmnews.report.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.domain.view.ReportRow;
import net.yozmnews.report.domain.view.ReportViewRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 직전 리포트 읽기 — 끝이 이번 시작과 같은 같은 종류의 시작한 리포트만 직전 리포트다. 시작하지 않고 끝난 실패 리포트(쓸 모델 없음 · 시작
 * 기한 지남)는 스냅숏이 없어 직전 리포트가 아니다. 실제 PostgreSQL 위에서 리포트 행을 직접 넣는다.
 * <p>
 * 지키는 규칙: report-R18
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class JdbcReportViewRepositoryTests {

	private static final UserId USER = new UserId(7);

	private static final Instant START = Instant.parse("2026-11-09T00:00:00Z");

	private static final Instant END = Instant.parse("2026-11-16T00:00:00Z");

	@Autowired
	ReportViewRepository views;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clear() {
		this.jdbc.sql("delete from reports").update();
	}

	@Test
	void aFailedReportThatNeverStartedIsNotThePreviousReport() {
		failedWithoutStarting(USER, START, END);

		assertThat(this.views.previous(USER, ReportPeriodKind.WEEK, END)).isEmpty();
	}

	@Test
	void aFailedReportThatStartedIsThePreviousReport() {
		// 대조 — 시작한 뒤 실패한 리포트는 스냅숏이 있어 직전 값을 꺼낼 수 있다
		long id = failedAfterStarting(USER, START, END);

		assertThat(this.views.previous(USER, ReportPeriodKind.WEEK, END)).map(ReportRow::id).contains(new ReportId(id));
		// 대조 — 다른 종류 · 다른 끝은 직전 리포트가 아니다
		assertThat(this.views.previous(USER, ReportPeriodKind.MONTH, END)).isEmpty();
		assertThat(this.views.previous(USER, ReportPeriodKind.WEEK, START)).isEmpty();
	}

	private long failedWithoutStarting(UserId user, Instant start, Instant end) {
		return insert(user, start, end, null, null);
	}

	private long failedAfterStarting(UserId user, Instant start, Instant end) {
		return insert(user, start, end, end, "{\"version\":1}");
	}

	private long insert(UserId user, Instant start, Instant end, @Nullable Instant started, @Nullable String snapshot) {
		return this.jdbc.sql("""
				insert into reports (user_id, period_kind, period_start, period_end, status, provider, model, stats_snapshot,
				                     error_code, error_message, created_at, started_at, completed_at, zone, dispatch_token,
				                     dispatched_at, scheduled_at, regenerations, version)
				values (:user, 'WEEK', :start, :end, 'FAILED', :provider, :model, cast(:snapshot as jsonb), :error, '실패',
				        :end, :started, :end, 'Asia/Seoul', gen_random_uuid(), :end, :end, 0, 1)
				returning id""")
			.param("user", user.value())
			.param("start", start.atOffset(ZoneOffset.UTC))
			.param("end", end.atOffset(ZoneOffset.UTC))
			.param("provider", started == null ? null : "openai", java.sql.Types.VARCHAR)
			.param("model", started == null ? null : "gpt-5.5-mini", java.sql.Types.VARCHAR)
			.param("snapshot", snapshot, java.sql.Types.VARCHAR)
			.param("error", started == null ? "NO_USABLE_MODEL" : "PROVIDER_UNAVAILABLE")
			.param("started", started == null ? null : started.atOffset(ZoneOffset.UTC), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
			.query(Long.class)
			.single();
	}

}
