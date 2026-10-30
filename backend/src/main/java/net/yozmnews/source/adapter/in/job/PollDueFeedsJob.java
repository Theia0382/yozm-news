package net.yozmnews.source.adapter.in.job;

import java.util.UUID;

import net.yozmnews.source.application.ClaimedPoll;
import net.yozmnews.source.application.PollScheduler;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Component;

/**
 * 수집할 피드 등록 반복 작업 — docs/specs/source.md §4 · source-R15 · source-R18 · source-R19.
 * <p>
 * JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@Component
public class PollDueFeedsJob {

	static final String ID = "poll-due-feeds";

	private final PollScheduler scheduler;

	private final JobScheduler jobs;

	PollDueFeedsJob(PollScheduler scheduler, JobScheduler jobs) {
		this.scheduler = scheduler;
		this.jobs = jobs;
	}

	@Recurring(id = ID, interval = "PT1M")
	@Job(name = "수집할 피드 등록", retries = 0)
	public void run() {
		for (ClaimedPoll poll : this.scheduler.claimDueFeeds()) {
			long feedId = poll.feedId().value();
			UUID token = poll.token();
			this.jobs.<PollFeedJob>schedule(token, poll.scheduledAt(), job -> job.run(feedId, token));
		}
	}

}
