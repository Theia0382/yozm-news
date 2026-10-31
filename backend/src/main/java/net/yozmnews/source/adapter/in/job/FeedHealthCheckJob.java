package net.yozmnews.source.adapter.in.job;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.source.application.FeedHealthCheck;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 피드 건강 검사 반복 작업 — docs/specs/source.md §4 · source-R47.
 * <p>
 * JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class FeedHealthCheckJob {

	static final String ID = "feed-health-check";

	private final FeedHealthCheck check;

	FeedHealthCheckJob(FeedHealthCheck check) {
		this.check = check;
	}

	@Recurring(id = ID, interval = "PT1H")
	@Job(name = "피드 건강 검사", retries = 0)
	public void run() {
		this.check.run();
	}

}
