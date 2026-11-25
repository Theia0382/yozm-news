package net.yozmnews.report.adapter.in.job;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.report.application.StalledReports;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 멈춘 리포트 거두기 반복 작업 — 5분마다 시작되지 않은 등록을 다시 등록하거나 끝내고, 임대가 지난 생성을 끝낸다. docs/specs/report.md §4
 * <p>
 * 재시도하지 않는다 — 다음 회차가 잇는다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class RecoverStalledReportsJob {

	static final String ID = "recover-stalled-reports";

	private final StalledReports stalled;

	RecoverStalledReportsJob(StalledReports stalled) {
		this.stalled = stalled;
	}

	@Recurring(id = ID, interval = "PT5M")
	@Job(name = "멈춘 리포트 거두기", retries = 0)
	public void run() {
		this.stalled.runRound();
	}

}
