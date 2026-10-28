package net.yozmnews.source.adapter.in.job;

import java.util.UUID;

import net.yozmnews.source.application.ClaimedPoll;
import net.yozmnews.source.application.PollScheduler;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Component;

/**
 * 1분마다 지금 수집할 피드를 점유하고, 피드마다 수집 작업을 예약 등록하는 반복 작업. 등록은 점유를 커밋한 뒤에 한다 — 작업 저장소는 그
 * 트랜잭션에 참여하지 않는다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
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

	/**
	 * 재시도하지 않는다 — 실패한 회차가 재시도를 기다리는 동안 JobRunr 는 이 작업의 다음 회차를 건너뛰므로, 다음 회차가 곧 재시도다. 수집
	 * 작업의 id 는 점유 토큰이라, 같은 점유를 두 번 등록해도 작업은 하나다.
	 */
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
