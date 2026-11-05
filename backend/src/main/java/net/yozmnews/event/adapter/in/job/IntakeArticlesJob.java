package net.yozmnews.event.adapter.in.job;

import net.yozmnews.event.application.EventIntakeRound;
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import net.yozmnews.platform.WorkerRole;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 사건 묶기 대기 반복 작업 — docs/specs/event.md §4 · event-R6 · event-R7.
 * <p>
 * 임베더는 부를 때 찾고, 없으면 회차를 건너뛴다. 반복 작업은 이전 실행이 대기 · 처리 중이면 그 회차를 건너뛰고, 실패해도 재시도하지 않는다.
 * JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class IntakeArticlesJob {

	static final String ID = "event-intake";

	static final String NO_EMBEDDER = "임베더가 없어 사건 묶기 대기 기사를 처리하지 않는다 — 기사는 대기에 쌓인다";

	private static final Logger logger = LoggerFactory.getLogger(IntakeArticlesJob.class);

	private final EventIntakeRound round;

	private final ObjectProvider<ArticleEmbedder> embedders;

	IntakeArticlesJob(EventIntakeRound round, ObjectProvider<ArticleEmbedder> embedders) {
		this.round = round;
		this.embedders = embedders;
		if (embedders.getIfAvailable() == null) {
			logger.warn(NO_EMBEDDER);
		}
	}

	@Recurring(id = ID, interval = "PT1M")
	@Job(name = "사건 묶기 대기 기사 처리", retries = 0)
	public void run() {
		ArticleEmbedder embedder = this.embedders.getIfAvailable();
		if (embedder == null) {
			return;
		}
		this.round.run(embedder);
	}

}
