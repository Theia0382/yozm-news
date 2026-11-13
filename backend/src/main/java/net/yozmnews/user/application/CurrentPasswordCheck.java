package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import net.yozmnews.user.domain.throttle.EmailDigest;
import net.yozmnews.user.domain.throttle.ThrottledAttempt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 사용자의 현재 비밀번호 확인 — 비밀번호 변경 · 복구 코드 재발급이 함께 쓰는 정본. docs/specs/user.md §4 · user-R19
 * <p>
 * 그 계정 이메일의 로그인 실패 기록에 걸어 대조한다 — 같은 비밀의 추측 예산이 로그인과 하나다. 대조 전에 자격 행을 잠가, 같은 계정의 다른 자격
 * 변경과 줄을 선 뒤의 해시로 대조한다. 부르는 쪽의 트랜잭션 안에서만 돈다.
 */
@Component
class CurrentPasswordCheck {

	private final AccountRepository accounts;

	private final PasswordCredentialRepository credentials;

	private final PasswordHashing hashing;

	private final FailureThrottle throttle;

	CurrentPasswordCheck(AccountRepository accounts, PasswordCredentialRepository credentials, PasswordHashing hashing,
			FailureThrottle throttle) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.credentials = Objects.requireNonNull(credentials, "credentials");
		this.hashing = Objects.requireNonNull(hashing, "hashing");
		this.throttle = Objects.requireNonNull(throttle, "throttle");
	}

	/**
	 * 현재 비밀번호를 대조한다.
	 * @return 맞으면 잠근 자격, 틀리면 거절, 연속 실패면 대기
	 * @throws IllegalStateException 세션 사용자의 쓰는 계정 · 자격이 없을 때 — 세션 체인이 요청마다 계정을 확인하므로 정상 경로에서는 없다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	Throttled<PasswordCredential> check(UserId user, String rawCurrentPassword, Instant now) {
		Account account = this.accounts.findById(user.value())
			.filter(Account::active)
			.orElseThrow(() -> new IllegalStateException("세션 사용자의 쓰는 계정이 없다: " + user.value()));
		String password = PasswordPolicy.normalize(rawCurrentPassword);
		return this.throttle.attempt(ThrottledAttempt.SIGN_IN, EmailDigest.of(account.email().value()), now, () -> {
			this.credentials.lockForUpdate(user.value());
			PasswordCredential credential = this.credentials.findById(user.value())
				.orElseThrow(() -> new IllegalStateException("세션 사용자의 자격이 없다: " + user.value()));
			boolean matches = PasswordPolicy.comparable(password)
					&& this.hashing.matches(password, credential.passwordHash());
			return matches ? Optional.of(credential) : Optional.empty();
		});
	}

	@Override
	public String toString() {
		return "CurrentPasswordCheck";
	}

}
