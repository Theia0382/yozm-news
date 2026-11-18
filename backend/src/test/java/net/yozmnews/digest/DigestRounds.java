package net.yozmnews.digest;

import java.util.List;

import org.jobrunr.scheduling.JobScheduler;

/**
 * 시험 도우미 — 다이제스트의 반복 작업을 지운다. 기본 profile 은 작업 역할을 켜 시험 컨텍스트에서도 작업 서버가 돈다 — 일정 회차는 실제 시계로
 * 행을 만들고, 거두기 회차는 고정 시계로 넣은 오래된 행을 다시 등록하거나 끝낸다. 다이제스트 행을 만드는 시험이 각 시험 앞에서 불러 그것을
 * 막는다(수집 시험의 {@code deleteRecurringJob("poll-due-feeds")} 꼴).
 */
public final class DigestRounds {

	/** 다이제스트의 반복 작업 ID — 일정 회차 · 거두기 회차 */
	static final List<String> IDS = List.of("schedule-due-digests", "recover-stalled-digests");

	private DigestRounds() {
	}

	/** 다이제스트의 반복 작업을 작업 저장소에서 지운다 */
	public static void stop(JobScheduler jobs) {
		for (String id : IDS) {
			jobs.deleteRecurringJob(id);
		}
	}

}
