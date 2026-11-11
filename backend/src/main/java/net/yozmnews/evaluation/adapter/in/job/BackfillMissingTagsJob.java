package net.yozmnews.evaluation.adapter.in.job;

import net.yozmnews.evaluation.application.TagBackfillRound;
import net.yozmnews.platform.WorkerRole;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 빠진 태그 채움 반복 작업 — docs/specs/evaluation.md §4.
 * <p>
 * 반복 작업은 이전 실행이 대기 · 처리 중이면 그 회차를 건너뛰고, 실패해도 재시도하지 않는다 — 다음 회차가 채움을 다시 점유해 커서부터 잇는다.
 * JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다. 평가기는 필수다 — 원본 응답의 형식은 평가기가 안다.
 */
@WorkerRole
@Component
public class BackfillMissingTagsJob {

	static final String ID = "backfill-missing-tags";

	private final TagBackfillRound round;


	BackfillMissingTagsJob(TagBackfillRound round) {
		this.round = round;
	}

	@Recurring(id = ID, interval = "PT1M")
	@Job(name = "빠진 태그 채움", retries = 0)
	public void run() {
		this.round.run();
	}

}
