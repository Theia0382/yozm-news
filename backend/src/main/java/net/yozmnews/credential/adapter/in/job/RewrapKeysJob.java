package net.yozmnews.credential.adapter.in.job;

import net.yozmnews.credential.application.DekRewrap;
import net.yozmnews.platform.WorkerRole;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 재암호화 반복 작업 — docs/specs/credential.md §4 · credential-R7.
 * <p>
 * 실패해도 재시도하지 않는다 — 다음 회차가 잇는다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class RewrapKeysJob {

	static final String ID = "rewrap-llm-keys";

	private final DekRewrap rewrap;

	RewrapKeysJob(DekRewrap rewrap) {
		this.rewrap = rewrap;
	}

	@Recurring(id = ID, interval = "PT10M")
	@Job(name = "LLM 키 KEK 재암호화", retries = 0)
	public void run() {
		this.rewrap.run();
	}

}
