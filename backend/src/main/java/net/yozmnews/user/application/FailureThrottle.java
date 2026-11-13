package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import net.yozmnews.user.domain.throttle.EmailDigest;
import net.yozmnews.user.domain.throttle.FailureRecord;
import net.yozmnews.user.domain.throttle.LoginFailures;
import net.yozmnews.user.domain.throttle.LoginThrottle;
import net.yozmnews.user.domain.throttle.ThrottledAttempt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실패 지연을 거는 대조 한 번 — 로그인 · 현재 비밀번호 확인 · 복구 코드 대조가 함께 쓰는 단계의 정본. docs/specs/user.md §4 · user-R18 ·
 * user-R19
 * <p>
 * 이 종류 · 이메일의 실패 기록을 잠그고(없으면 만든다) → 기다릴 때면 대조 없이 대기 → 아니면 대조 → 맞으면 기록을 지우고 틀리면 기록을 올린다.
 * 잠금이 대조 동안 이어져 같은 종류 · 같은 이메일의 동시 시도는 줄을 선다. 부르는 쪽의 트랜잭션 안에서만 돈다.
 */
@Component
class FailureThrottle {

	private final LoginFailures failures;

	FailureThrottle(LoginFailures failures) {
		this.failures = Objects.requireNonNull(failures, "failures");
	}

	/**
	 * 대조 한 번.
	 * @param verify 대조 — 맞으면 값, 틀리면 비어 있다. 기다릴 때는 부르지 않는다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	<T> Throttled<T> attempt(ThrottledAttempt attempt, EmailDigest digest, Instant now, Supplier<Optional<T>> verify) {
		FailureRecord record = this.failures.lock(attempt, digest);
		Optional<Instant> retryAt = LoginThrottle.retryAt(record, now);
		if (retryAt.isPresent()) {
			return new Throttled.Wait<>(retryAt.get());
		}
		Optional<T> verified = verify.get();
		if (verified.isPresent()) {
			this.failures.forget(attempt, digest);
			return new Throttled.Passed<>(verified.get());
		}
		this.failures.record(attempt, digest, LoginThrottle.failed(record, now));
		return new Throttled.Failed<>();
	}

	@Override
	public String toString() {
		return "FailureThrottle";
	}

}
