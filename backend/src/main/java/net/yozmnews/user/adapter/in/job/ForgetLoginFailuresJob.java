package net.yozmnews.user.adapter.in.job;

import net.yozmnews.platform.WorkerRole;
import net.yozmnews.user.application.LoginFailureCleanup;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 오래된 로그인 실패 기록 정리 반복 작업 — docs/specs/user.md §4 · user-R19.
 * <p>
 * 실패해도 재시도하지 않는다 — 다음 회차가 이어 지운다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class ForgetLoginFailuresJob {

	static final String ID = "forget-login-failures";

	private final LoginFailureCleanup cleanup;

	ForgetLoginFailuresJob(LoginFailureCleanup cleanup) {
		this.cleanup = cleanup;
	}

	@Recurring(id = ID, interval = "PT1H")
	@Job(name = "오래된 로그인 실패 기록 정리", retries = 0)
	public void run() {
		this.cleanup.run();
	}

}
