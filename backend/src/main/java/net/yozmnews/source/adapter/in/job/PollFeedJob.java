package net.yozmnews.source.adapter.in.job;

import java.util.UUID;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.application.FeedPoller;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

/**
 * 피드 수집 작업 — docs/specs/source.md §4 · source-R16 ~ source-R21.
 * <p>
 * JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class PollFeedJob {

	private final FeedPoller poller;

	PollFeedJob(FeedPoller poller) {
		this.poller = poller;
	}

	@Job(name = "피드 수집 %0", retries = 0)
	public void run(long feedId, UUID leaseToken) {
		this.poller.poll(new FeedId(feedId), leaseToken);
	}

}
