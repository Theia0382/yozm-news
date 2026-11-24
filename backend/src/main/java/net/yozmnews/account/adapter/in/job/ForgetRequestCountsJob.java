package net.yozmnews.account.adapter.in.job;

import net.yozmnews.account.application.RequestCountCleanup;
import net.yozmnews.platform.WorkerRole;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 끝난 요청 제한 창 정리 반복 작업 — docs/specs/user.md §4 · user-R42.
 * <p>
 * 실패해도 재시도하지 않는다 — 다음 회차가 이어 지운다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class ForgetRequestCountsJob {

	static final String ID = "forget-request-counts";

	private final RequestCountCleanup cleanup;

	ForgetRequestCountsJob(RequestCountCleanup cleanup) {
		this.cleanup = cleanup;
	}

	@Recurring(id = ID, interval = "PT10M")
	@Job(name = "끝난 요청 제한 창 정리", retries = 0)
	public void run() {
		this.cleanup.run();
	}

}
