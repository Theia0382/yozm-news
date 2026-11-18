package net.yozmnews.digest;

import java.util.List;

import org.jobrunr.scheduling.JobScheduler;

/**
 * 시험 도우미 — 다이제스트의 반복 작업을 지운다. 기본 profile 은 작업 역할을 켜 시험 컨텍스트에서도 작업 서버가 돌므로, 일정 회차가 실제 시계로
 * 돌아 시험이 넣은 다이제스트 행 · 사용자를 건드리지 않게 다이제스트 행을 만드는 시험이 각 시험 앞에서 부른다(수집 시험의
 * {@code deleteRecurringJob("poll-due-feeds")} 꼴).
 */
public final class DigestRounds {

	/** 다이제스트의 반복 작업 ID — 일정 회차 */
	static final List<String> IDS = List.of("schedule-due-digests");

	private DigestRounds() {
	}

	/** 다이제스트의 반복 작업을 작업 저장소에서 지운다 */
	public static void stop(JobScheduler jobs) {
		for (String id : IDS) {
			jobs.deleteRecurringJob(id);
		}
	}

}
