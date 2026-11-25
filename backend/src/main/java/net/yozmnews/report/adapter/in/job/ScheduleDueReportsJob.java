package net.yozmnews.report.adapter.in.job;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.report.application.ReportScheduling;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 일정 리포트 등록 반복 작업 — 10분마다 리포트 시점이 된 사용자의 주간 · 월간 리포트를 만들고 생성 작업을 등록한다. docs/specs/report.md §4
 * <p>
 * 재시도하지 않는다 — 다음 회차가 잇는다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class ScheduleDueReportsJob {

	static final String ID = "schedule-due-reports";

	private final ReportScheduling scheduling;

	ScheduleDueReportsJob(ReportScheduling scheduling) {
		this.scheduling = scheduling;
	}

	@Recurring(id = ID, interval = "PT10M")
	@Job(name = "일정 리포트 등록", retries = 0)
	public void run() {
		this.scheduling.runRound();
	}

}
