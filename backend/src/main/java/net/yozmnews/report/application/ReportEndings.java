package net.yozmnews.report.application;

import java.time.ZoneId;
import java.util.Objects;

import net.yozmnews.report.domain.generator.ReportFailureCauses;
import net.yozmnews.report.domain.report.Report;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.report.domain.report.ReportRepository;
import net.yozmnews.user.ReportFailed;
import net.yozmnews.user.ReportFailedKind;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 끝낸 리포트 저장의 정본 — 생성 · 생성 작업의 시작 전 끝내기 · 거두기가 모두 이것으로 끝낸다. 실패면 같은 트랜잭션에서 {@code user} 가 선언한
 * {@link ReportFailed} 를 낸다. docs/specs/report.md §4
 * <p>
 * 끝나지 않은 행은 거부하고 트랜잭션 안에서만 저장한다 — 부르는 쪽이 행을 잠근 같은 트랜잭션이다. 실패 알림의 기간 날짜는 행의 시간대(시작했으면
 * 시작 때의 것, 시작하지 않고 끝났으면 끝내기 직전에 쓴 것)로 적는다 — 날짜 규칙의 정본은 {@link ReportPeriod}, 갈래의 정본은
 * {@link ReportFailureCauses} 다.
 */
@Component
class ReportEndings {

	/** 트랜잭션 밖에서 불렀을 때의 예외 메시지 */
	static final String OUTSIDE_TRANSACTION = "끝낸 리포트를 트랜잭션 밖에서 저장하려 했다 — 행을 잠근 트랜잭션에서 저장한다";

	private final ReportRepository reports;

	private final ApplicationEventPublisher events;

	ReportEndings(ReportRepository reports, ApplicationEventPublisher events) {
		this.reports = Objects.requireNonNull(reports, "reports");
		this.events = Objects.requireNonNull(events, "events");
	}

	/**
	 * @return 저장한 리포트
	 * @throws IllegalArgumentException 끝나지 않은 리포트일 때
	 * @throws IllegalStateException 트랜잭션 밖에서 불렀을 때
	 */
	Report save(Report finished) {
		Objects.requireNonNull(finished, "finished");
		if (!finished.status().finished()) {
			throw new IllegalArgumentException("끝나지 않은 리포트다: " + finished.status());
		}
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(OUTSIDE_TRANSACTION);
		}
		Report saved = this.reports.save(finished);
		// 망라 switch 식 — 상태가 늘면 컴파일 오류가 난다
		boolean failure = switch (saved.status()) {
			case SCHEDULED, RUNNING -> throw new IllegalStateException("끝나지 않은 리포트를 저장했다: " + saved.status());
			case READY, EMPTY -> false;
			case FAILED -> true;
		};
		if (failure) {
			this.events.publishEvent(failed(saved));
		}
		return saved;
	}

	/** 실패한 리포트의 알림 이벤트 — 기간 날짜는 행의 시간대로 */
	private ReportFailed failed(Report report) {
		ZoneId zone = ZoneId.of(this.reports.zoneOf(report.id().value())
			.orElseThrow(() -> new IllegalStateException("끝난 리포트에 시간대가 없다: " + report.id().value())));
		ReportPeriod period = report.period();
		ReportFailedKind kind = switch (period.kind()) {
			case WEEK -> ReportFailedKind.WEEK;
			case MONTH -> ReportFailedKind.MONTH;
		};
		return new ReportFailed(report.userId(), report.id().value(), kind, ReportPeriod.firstDay(period.start(), zone),
				ReportPeriod.lastDay(period.end(), zone), ReportFailureCauses.of(report.failure().orElseThrow()),
				report.completedAt().orElseThrow());
	}

}
