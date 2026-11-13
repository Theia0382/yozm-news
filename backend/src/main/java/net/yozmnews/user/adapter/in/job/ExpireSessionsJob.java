package net.yozmnews.user.adapter.in.job;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.user.application.SessionExpiry;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 만료 세션 정리 반복 작업 — docs/specs/user.md §4 · user-R12.
 * <p>
 * 실패해도 재시도하지 않는다 — 다음 회차가 이어 지운다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class ExpireSessionsJob {

	static final String ID = "expire-sessions";

	private final SessionExpiry expiry;

	ExpireSessionsJob(SessionExpiry expiry) {
		this.expiry = expiry;
	}

	@Recurring(id = ID, interval = "PT10M")
	@Job(name = "만료 세션 정리", retries = 0)
	public void run() {
		this.expiry.cleanUp();
	}

}
