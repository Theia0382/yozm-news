package net.yozmnews.source.adapter.in.job;

import java.util.UUID;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.application.FeedPoller;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

/**
 * 피드 하나를 한 번 수집하는 작업 — {@link PollDueFeedsJob} 이 점유 토큰을 작업 id 로 예약 등록한다. JobRunr 가 다른 패키지에서 반사로
 * 부르므로 클래스가 public 이어야 한다.
 */
@Component
public class PollFeedJob {

	private final FeedPoller poller;

	PollFeedJob(FeedPoller poller) {
		this.poller = poller;
	}

	/**
	 * 재시도하지 않는다 — 실패하면 점유가 만료된 뒤 {@link PollDueFeedsJob} 이 다시 등록한다. 같은 토큰으로 다시 실행해도 점유가 이미
	 * 풀렸으므로 아무것도 하지 않는다.
	 */
	@Job(name = "피드 수집 %0", retries = 0)
	public void run(long feedId, UUID leaseToken) {
		this.poller.poll(new FeedId(feedId), leaseToken);
	}

}
