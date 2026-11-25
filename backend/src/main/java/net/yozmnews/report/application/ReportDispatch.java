package net.yozmnews.report.application;

import java.util.Objects;

import net.yozmnews.report.domain.report.ReportJobQueue;
import net.yozmnews.report.domain.report.ScheduledReport;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 생성 작업 등록의 정본 — 리포트 행을 만들거나 바꾼 트랜잭션이 커밋된 뒤 그 토큰으로 생성 작업을 등록한다. 일정 · 거두기 · 다시 만들기가 함께
 * 쓴다. docs/specs/report.md §4
 * <p>
 * 작업 저장소는 업무 트랜잭션에 참여하지 않는다 — 트랜잭션 안에서 등록하면 커밋되지 않은 행의 작업이 먼저 돌 수 있어 거부한다. 등록 전에 멈춘
 * 행은 거두기가 다시 등록한다.
 */
@Component
class ReportDispatch {

	/** 트랜잭션 안에서 불렀을 때의 예외 메시지 */
	static final String IN_TRANSACTION = "생성 작업을 트랜잭션 안에서 등록하려 했다 — 행을 만든 트랜잭션이 커밋된 뒤 등록한다";

	private final ReportJobQueue queue;

	ReportDispatch(ReportJobQueue queue) {
		this.queue = Objects.requireNonNull(queue, "queue");
	}

	/**
	 * @throws IllegalStateException 트랜잭션 안에서 불렀을 때 — 등록하지 않는다
	 */
	void dispatch(ScheduledReport scheduled) {
		Objects.requireNonNull(scheduled, "scheduled");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(IN_TRANSACTION);
		}
		this.queue.enqueue(scheduled.id(), scheduled.token());
	}

}
