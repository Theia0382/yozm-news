package net.yozmnews.digest.adapter.in.job;

import net.yozmnews.digest.application.StalledDigests;
import net.yozmnews.platform.WorkerRole;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 멈춘 다이제스트 거두기 반복 작업 — 5분마다 시작되지 않은 다이제스트를 다시 등록하고 끝나지 않는 다이제스트를 실패로 끝낸다.
 * docs/specs/digest.md §4
 * <p>
 * 재시도하지 않는다 — 다음 회차가 잇는다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class RecoverStalledDigestsJob {

	static final String ID = "recover-stalled-digests";

	private final StalledDigests stalled;

	RecoverStalledDigestsJob(StalledDigests stalled) {
		this.stalled = stalled;
	}

	@Recurring(id = ID, interval = "PT5M")
	@Job(name = "멈춘 다이제스트 거두기", retries = 0)
	public void run() {
		this.stalled.runRound();
	}

}
