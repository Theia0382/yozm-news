package net.yozmnews.tag.adapter.in.job;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.tag.application.EvaluationRound;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 평가 대기 기사 평가 반복 작업 — docs/specs/tag.md §4 · tag-R19 · tag-R20.
 * <p>
 * 반복 작업은 이전 실행이 대기 · 처리 중이면 그 회차를 건너뛰고, 실패해도 재시도하지 않는다 — 다음 회차가 다시 점유한다. JobRunr 가 다른
 * 패키지에서 반사로 부르므로 클래스가 public 이어야 한다. 판정은 회차가 {@code evaluation} 의 태그 판정으로 한다 — 작업 역할의
 * 평가기 요건은 {@code evaluation} 이 지킨다(ADR-0061).
 */
@WorkerRole
@Component
public class EvaluatePendingArticlesJob {

	static final String ID = "evaluate-pending-articles";

	private final EvaluationRound round;


	EvaluatePendingArticlesJob(EvaluationRound round) {
		this.round = round;
	}

	@Recurring(id = ID, interval = "PT1M")
	@Job(name = "평가 대기 기사 평가", retries = 0)
	public void run() {
		this.round.run();
	}

}
