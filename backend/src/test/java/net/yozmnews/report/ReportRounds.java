package net.yozmnews.report;

import java.util.List;

import org.jobrunr.scheduling.JobScheduler;

/**
 * 시험 도우미 — 리포트의 반복 작업을 지운다. 기본 profile 은 작업 역할을 켜 시험 컨텍스트에서도 작업 서버가 돈다 — 일정 회차는 실제 시계로 행을
 * 만들 수 있다. 리포트 일정 · 거두기를 고정 시계로 보는 시험이 각 시험 앞에서 불러 그것을 막는다(다이제스트의 {@code DigestRounds} 꼴).
 */
public final class ReportRounds {

	/** 리포트의 반복 작업 ID — 일정 회차 */
	static final List<String> IDS = List.of("schedule-due-reports");

	private ReportRounds() {
	}

	/** 리포트의 반복 작업을 작업 저장소에서 지운다 */
	public static void stop(JobScheduler jobs) {
		for (String id : IDS) {
			jobs.deleteRecurringJob(id);
		}
	}

}
