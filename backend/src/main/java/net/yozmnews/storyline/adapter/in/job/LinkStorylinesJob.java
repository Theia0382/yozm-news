package net.yozmnews.storyline.adapter.in.job;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.storyline.application.LinkingRound;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 잇기 대기 반복 작업 — docs/specs/storyline.md §4 · storyline-R3.
 * <p>
 * 반복 작업은 이전 실행이 대기 · 처리 중이면 그 회차를 건너뛰고, 실패해도 재시도하지 않는다. JobRunr 가 다른 패키지에서 반사로 부르므로
 * 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class LinkStorylinesJob {

	static final String ID = "storyline-linking";

	private final LinkingRound round;

	LinkStorylinesJob(LinkingRound round) {
		this.round = round;
	}

	@Recurring(id = ID, interval = "PT1M")
	@Job(name = "스토리라인 잇기 대기 사건 처리", retries = 0)
	public void run() {
		this.round.run();
	}

}
