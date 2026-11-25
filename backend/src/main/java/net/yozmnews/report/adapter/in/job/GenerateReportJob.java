package net.yozmnews.report.adapter.in.job;

import java.util.UUID;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.application.ReportRun;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

/**
 * 리포트 생성 작업 — 리포트 하나를 생성한다. 작업 ID 는 행의 등록 토큰이다. docs/specs/report.md §4
 * <p>
 * 재시도하지 않는다 — 생성 중인 리포트는 다시 시작하지 않고, 멈춘 행은 거두기가 다룬다. 토큰은 작업 ID 일 뿐 판단에 쓰지 않는다. JobRunr 가
 * 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class GenerateReportJob {

	private final ReportRun run;

	GenerateReportJob(ReportRun run) {
		this.run = run;
	}

	@Job(name = "리포트 생성 %0", retries = 0)
	public void run(long reportId, UUID token) {
		this.run.run(new ReportId(reportId));
	}

}
