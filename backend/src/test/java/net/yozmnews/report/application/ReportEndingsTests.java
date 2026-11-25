package net.yozmnews.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.domain.report.Completion;
import net.yozmnews.report.domain.report.Report;
import net.yozmnews.report.domain.report.ReportRepository;
import net.yozmnews.user.GenerationFailureCause;
import net.yozmnews.user.ReportFailed;
import net.yozmnews.user.ReportFailedKind;
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
 * 끝낸 리포트 저장의 정본 — 끝난 행만, 트랜잭션 안에서만 저장한다. 시작하지 않고 끝내는 행은 시간대를 먼저 쓴다(테이블 제약). 실제
 * PostgreSQL.
 * <p>
 * 지키는 규칙: report-R1
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@RecordApplicationEvents
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

	@Autowired
	ApplicationEvents events;

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
	void aFailedReportPublishesItsFailureWithThePeriodDaysInTheRowZone() {
		Report scheduled = this.reports.save(Report.schedule(new UserId(7), ReportFixtures.PERIOD, UUID.randomUUID(), NOW));

		Report saved = this.transaction.execute(status -> {
			this.reports.writeZone(scheduled.id().value(), "America/Denver");
			return this.endings.save(scheduled.abandon(ReportFailure.INTERRUPTED, "멈췄다", NOW));
		});

		// 기간 [2026-11-16T00:00Z, 11-23T00:00Z) 를 덴버(UTC−7)로 적으면 11-15 ~ 11-22 — UTC 로 적었다면 11-16 ~ 11-22
		assertThat(this.events.stream(ReportFailed.class)).containsExactly(new ReportFailed(new UserId(7),
				saved.id().value(), ReportFailedKind.WEEK, LocalDate.parse("2026-11-15"), LocalDate.parse("2026-11-22"),
				GenerationFailureCause.TEMPORARY, NOW));
	}

	@Test
	void aReportThatEndedWithoutFailingPublishesNothing() {
		Report scheduled = this.reports.save(Report.schedule(new UserId(7), ReportFixtures.PERIOD, UUID.randomUUID(), NOW));

		this.transaction.executeWithoutResult(status -> {
			this.reports.writeStart(scheduled.id().value(), "{}", null, "Asia/Seoul");
			Report running = this.reports.save(this.reports.lock(scheduled.id().value())
				.orElseThrow()
				.start(ReportFixtures.MODEL, NOW));
			this.endings.save(running.complete(new Completion.Empty(), NOW));
		});

		assertThat(this.jdbc.sql("select status from reports where id = :id")
			.param("id", scheduled.id().value())
			.query(String.class)
			.single()).isEqualTo("EMPTY");
		assertThat(this.events.stream(ReportFailed.class)).isEmpty();
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
