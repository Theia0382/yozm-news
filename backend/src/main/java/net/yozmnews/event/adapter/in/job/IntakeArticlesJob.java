package net.yozmnews.event.adapter.in.job;

import net.yozmnews.event.application.AssignmentRound;
import net.yozmnews.event.application.EventIntakeRound;
import net.yozmnews.event.application.MergeRound;
import net.yozmnews.event.application.RejudgementRound;
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import net.yozmnews.platform.WorkerRole;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 사건 묶기 대기 반복 작업 — docs/specs/event.md §4 · event-R6 · event-R7 · event-R13 · event-R16 · event-R18.
 * <p>
 * 표현 회차 → 배정 회차 → 재판정 회차 → 병합 회차를 차례로 부른다 — 한 작업이라 서로 겹치지 않는다. 임베더는 생성자로 받는다 — 없으면 작업
 * 역할이 뜨지 않는다(event-R7). 반복 작업은 이전 실행이 대기 · 처리 중이면 그 회차를 건너뛰고, 실패해도 재시도하지 않는다.
 * JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class IntakeArticlesJob {

	static final String ID = "event-intake";

	private final EventIntakeRound round;

	private final AssignmentRound assignment;

	private final RejudgementRound rejudgement;

	private final MergeRound merge;

	private final ArticleEmbedder embedder;

	IntakeArticlesJob(EventIntakeRound round, AssignmentRound assignment, RejudgementRound rejudgement,
			MergeRound merge, ArticleEmbedder embedder) {
		this.round = round;
		this.assignment = assignment;
		this.rejudgement = rejudgement;
		this.merge = merge;
		this.embedder = embedder;
	}

	@Recurring(id = ID, interval = "PT1M")
	@Job(name = "사건 묶기 대기 기사 처리", retries = 0)
	public void run() {
		this.round.run(this.embedder);
		this.assignment.run(this.embedder);
		this.rejudgement.run();
		this.merge.run();
	}

}
