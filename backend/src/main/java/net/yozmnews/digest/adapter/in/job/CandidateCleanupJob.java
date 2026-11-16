package net.yozmnews.digest.adapter.in.job;

import net.yozmnews.digest.application.CandidateCleanup;
import net.yozmnews.platform.WorkerRole;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 후보 행 보존 정리 반복 작업 — docs/specs/digest.md §4 · digest-R11
 * <p>
 * 실패해도 재시도하지 않는다 — 다음 회차가 잇는다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class CandidateCleanupJob {

	static final String ID = "digest-candidate-cleanup";

	private final CandidateCleanup cleanup;

	CandidateCleanupJob(CandidateCleanup cleanup) {
		this.cleanup = cleanup;
	}

	@Recurring(id = ID, interval = "P1D")
	@Job(name = "다이제스트 후보 행 정리", retries = 0)
	public void run() {
		this.cleanup.run();
	}

}
