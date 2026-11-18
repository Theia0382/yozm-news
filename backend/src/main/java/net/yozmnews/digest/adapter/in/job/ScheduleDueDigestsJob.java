package net.yozmnews.digest.adapter.in.job;

import net.yozmnews.digest.application.DigestScheduling;
import net.yozmnews.platform.WorkerRole;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 일정 다이제스트 등록 반복 작업 — 1분마다 생성 시각이 된 사용자의 다이제스트를 만들고 생성 작업을 등록한다. docs/specs/digest.md §4
 * <p>
 * 재시도하지 않는다 — 다음 회차가 잇는다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class ScheduleDueDigestsJob {

	static final String ID = "schedule-due-digests";

	private final DigestScheduling scheduling;

	ScheduleDueDigestsJob(DigestScheduling scheduling) {
		this.scheduling = scheduling;
	}

	@Recurring(id = ID, interval = "PT1M")
	@Job(name = "일정 다이제스트 등록", retries = 0)
	public void run() {
		this.scheduling.runRound();
	}

}
