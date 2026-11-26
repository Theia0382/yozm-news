package net.yozmnews.platform.adapter.in.job;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.platform.application.RoundRunCleanup;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 회차 기록 지우기 반복 작업 — docs/specs/platform.md §4 · 반복 회차.
 * <p>
 * 실패해도 재시도하지 않는다 — 다음 실행이 이어 지운다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class ForgetRoundRunsJob {

	static final String ID = "forget-round-runs";

	private final RoundRunCleanup cleanup;

	ForgetRoundRunsJob(RoundRunCleanup cleanup) {
		this.cleanup = cleanup;
	}

	@Recurring(id = ID, interval = "P1D")
	@Job(name = "오래된 회차 기록 지우기", retries = 0)
	public void run() {
		this.cleanup.run();
	}

}
