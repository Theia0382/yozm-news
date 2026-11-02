package net.yozmnews.evaluation.adapter.in.job;

import net.yozmnews.evaluation.application.EvaluationRound;
import net.yozmnews.platform.WorkerRole;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 평가 대기 기사 평가 반복 작업 — docs/specs/evaluation.md §4 · evaluation-R19 · evaluation-R20.
 * <p>
 * 반복 작업은 이전 실행이 대기 · 처리 중이면 그 회차를 건너뛰고, 실패해도 재시도하지 않는다 — 다음 회차가 다시 점유한다. JobRunr 가 다른
 * 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class EvaluatePendingArticlesJob {

	static final String ID = "evaluate-pending-articles";

	private static final Logger logger = LoggerFactory.getLogger(EvaluatePendingArticlesJob.class);

	private final EvaluationRound round;

	EvaluatePendingArticlesJob(EvaluationRound round) {
		this.round = round;
		if (!round.evaluatorAvailable()) {
			logger.warn("평가기가 없어 기사를 평가하지 않는다 — 기사는 평가 대기에 쌓인다");
		}
	}

	@Recurring(id = ID, interval = "PT1M")
	@Job(name = "평가 대기 기사 평가", retries = 0)
	public void run() {
		this.round.run();
	}

}
