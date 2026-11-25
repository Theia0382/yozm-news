package net.yozmnews.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.domain.report.Report;
import net.yozmnews.report.domain.report.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 끝낸 리포트 저장의 정본 — 끝난 행만, 트랜잭션 안에서만 저장한다. 시작하지 않고 끝내는 행은 시간대를 먼저 쓴다(테이블 제약). 실제
 * PostgreSQL.
 * <p>
 * 지키는 규칙: report-R1
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReportEndingsTests {

	private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

	@Autowired
	ReportEndings endings;

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
	void savesAReportThatEndedWithoutStartingAfterItsZoneIsWritten() {
		Report scheduled = this.reports.save(Report.schedule(new UserId(7), ReportFixtures.PERIOD, UUID.randomUUID(), NOW));

		Report saved = this.transaction.execute(status -> {
			this.reports.writeZone(scheduled.id().value(), "Asia/Seoul");
			return this.endings.save(scheduled.abandon(ReportFailure.NO_USABLE_MODEL, "쓸 모델이 없다", NOW));
		});

		assertThat(saved.status()).isEqualTo(ReportStatus.FAILED);
		assertThat(this.jdbc.sql("select status, zone, started_at is null as unstarted from reports where id = :id")
			.param("id", scheduled.id().value())
			.query()
			.singleRow()).containsEntry("status", "FAILED").containsEntry("zone", "Asia/Seoul").containsEntry("unstarted", true);
	}

	@Test
	void refusesAReportThatHasNotEndedOrACallOutsideATransaction() {
		Report scheduled = this.reports.save(Report.schedule(new UserId(7), ReportFixtures.PERIOD, UUID.randomUUID(), NOW));

		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.transaction.executeWithoutResult(status -> this.endings.save(scheduled)))
			.withMessage("끝나지 않은 리포트다: SCHEDULED");
		assertThatIllegalStateException()
			.isThrownBy(() -> this.endings.save(scheduled.abandon(ReportFailure.INTERRUPTED, "멈췄다", NOW)))
			.withMessage(ReportEndings.OUTSIDE_TRANSACTION);
		// 대조 — 거부한 두 번 모두 행은 생성을 기다린 채다
		assertThat(this.jdbc.sql("select status from reports where id = :id")
			.param("id", scheduled.id().value())
			.query(String.class)
			.single()).isEqualTo("SCHEDULED");
	}

}
