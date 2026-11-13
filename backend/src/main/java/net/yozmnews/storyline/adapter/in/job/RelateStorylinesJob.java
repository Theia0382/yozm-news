package net.yozmnews.storyline.adapter.in.job;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.storyline.application.RelationRound;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 관계 대기 반복 작업 — docs/specs/storyline.md §4 · storyline-R31.
 * <p>
 * 잇기 작업과 따로 돈다 — 관계 판정(Jev)이 느려도 잇기 회차가 밀리지 않는다. 반복 작업은 이전 실행이 대기 · 처리 중이면 그 회차를 건너뛰고,
 * 실패해도 재시도하지 않는다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class RelateStorylinesJob {

	static final String ID = "storyline-relation";

	private final RelationRound round;

	RelateStorylinesJob(RelationRound round) {
		this.round = round;
	}

	@Recurring(id = ID, interval = "PT1M")
	@Job(name = "스토리라인 관계 대기 처리", retries = 0)
	public void run() {
		this.round.run();
	}

}
