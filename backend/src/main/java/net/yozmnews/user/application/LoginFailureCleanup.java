package net.yozmnews.user.application;

import java.time.InstantSource;
import java.util.Objects;

import net.yozmnews.user.domain.throttle.LoginFailures;
import net.yozmnews.user.domain.throttle.LoginThrottle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 오래된 로그인 실패 기록 정리 유스케이스 — 마지막 실패에서 잊는 기간이 지난 기록을 지운다. docs/specs/user.md §4 · user-R19
 */
@Service
public class LoginFailureCleanup {

	private final LoginFailures failures;

	private final InstantSource clock;

	@Autowired
	LoginFailureCleanup(LoginFailures failures) {
		this(failures, InstantSource.system());
	}

	LoginFailureCleanup(LoginFailures failures, InstantSource clock) {
		this.failures = Objects.requireNonNull(failures, "failures");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 잊은 기록을 지운다.
	 * @return 지운 행 수
	 */
	public int run() {
		return this.failures.forgetOlderThan(LoginThrottle.forgetBefore(this.clock.instant()));
	}

}
