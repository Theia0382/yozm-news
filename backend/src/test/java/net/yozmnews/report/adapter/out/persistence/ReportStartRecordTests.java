package net.yozmnews.report.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.domain.report.Completion;
import net.yozmnews.report.domain.report.Report;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.report.domain.report.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시작의 기록(통계 스냅숏 · 직전 리포트 · 시간대) 비우기 — 시작한 뒤 실패한 리포트를 다시 생성을 기다리게 저장한 직후, 같은 트랜잭션에서 비운다.
 * 실제 PostgreSQL 의 테이블 제약 위에서 순서를 본다.
 * <p>
 * 지키는 규칙: report-R1
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReportStartRecordTests {

	private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

	private static final Instant LATER = Instant.parse("2026-11-25T00:00:00Z");

	private static final UUID AGAIN = UUID.fromString("00000000-0000-0000-0000-000000000242");

	@Autowired
	ReportRepository reports;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clear() {
		this.jdbc.sql("delete from reports").update();
	}

	@Test
	void aRegeneratedReportWaitsAgainWithoutTheRecordOfItsFormerStart() {
		UserId user = new UserId(7);
		long previous = this.reports.save(Report.schedule(user, new ReportPeriod(ReportPeriodKind.WEEK,
				ReportFixtures.PERIOD.start().minus(Duration.ofDays(7)), ReportFixtures.PERIOD.start()), UUID.randomUUID(),
				NOW)).id().value();
		Report scheduled = this.reports.save(Report.schedule(user, ReportFixtures.PERIOD, UUID.randomUUID(), NOW));
		long id = scheduled.id().value();
		Report failed = this.transaction.execute(status -> {
			this.reports.writeStart(id, "{\"version\":1}", previous, "Asia/Seoul");
			Report running = this.reports.save(scheduled.start(ReportFixtures.MODEL, NOW.plusSeconds(1)));
			return this.reports.save(running.complete(
					new Completion.Failed(ReportFailure.PROVIDER_UNAVAILABLE, "일시 장애", null, null), NOW.plusSeconds(5)));
		});

		// 대조 — 시작한 실패 행에서 먼저 비우면 시작한 행의 스냅숏 제약에 걸린다(저장 → 비우기 순서)
		assertThatThrownBy(() -> this.transaction.executeWithoutResult(status -> this.reports.clearStart(id)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("reports_snapshot");

		this.transaction.executeWithoutResult(status -> {
			this.reports.save(failed.regenerate(AGAIN, LATER));
			this.reports.clearStart(id);
		});

		assertThat(this.jdbc.sql("""
				select status, regenerations, dispatch_token = :token as new_token,
				       scheduled_at = :later and dispatched_at = :later as rescheduled,
				       started_at is null and provider is null and completed_at is null and error_code is null as unstarted,
				       stats_snapshot is null and previous_report_id is null and zone is null as cleared
				from reports where id = :id""")
			.param("token", AGAIN)
			.param("later", LATER.atOffset(ZoneOffset.UTC))
			.param("id", id)
			.query()
			.singleRow()).containsEntry("status", "SCHEDULED")
			.containsEntry("regenerations", 1)
			.containsEntry("new_token", true)
			.containsEntry("rescheduled", true)
			.containsEntry("unstarted", true)
			.containsEntry("cleared", true);
	}

}
