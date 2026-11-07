package net.yozmnews.event.adapter.in.job;

import net.yozmnews.event.application.CleanupRound;
import net.yozmnews.platform.WorkerRole;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 사건 정리 반복 작업 — 하루 한 번 닫기 · 장기 검색 인덱스 · 기사 표현 정리를 돈다 · docs/specs/event.md §4 · event-R13.
 * <p>
 * 임베더 · 평가기가 없어도 돈다. 실패해도 재시도하지 않는다 — 다음 날 같은 판정으로 다시 돈다. JobRunr 가 다른 패키지에서 반사로 부르므로
 * 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class EventCleanupJob {

	static final String ID = "event-cleanup";

	private final CleanupRound round;

	EventCleanupJob(CleanupRound round) {
		this.round = round;
	}

	@Recurring(id = ID, interval = "P1D")
	@Job(name = "사건 정리", retries = 0)
	public void run() {
		this.round.run();
	}

}
