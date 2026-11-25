package net.yozmnews.report.application;

import java.util.Objects;

import net.yozmnews.report.domain.report.Report;
import net.yozmnews.report.domain.report.ReportRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 끝낸 리포트 저장의 정본 — 생성 · 생성 작업의 시작 전 끝내기 · 거두기가 모두 이것으로 끝낸다. docs/specs/report.md §4
 * <p>
 * 끝나지 않은 행은 거부하고 트랜잭션 안에서만 저장한다 — 부르는 쪽이 행을 잠근 같은 트랜잭션이다.
 */
@Component
class ReportEndings {

	/** 트랜잭션 밖에서 불렀을 때의 예외 메시지 */
	static final String OUTSIDE_TRANSACTION = "끝낸 리포트를 트랜잭션 밖에서 저장하려 했다 — 행을 잠근 트랜잭션에서 저장한다";

	private final ReportRepository reports;

	ReportEndings(ReportRepository reports) {
		this.reports = Objects.requireNonNull(reports, "reports");
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
		return this.reports.save(finished);
	}

}
