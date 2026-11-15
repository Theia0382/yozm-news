package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import net.yozmnews.user.domain.throttle.EmailDigest;
import net.yozmnews.user.domain.throttle.ThrottledAttempt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 유스케이스 — 한 번의 시도가 트랜잭션 하나다. docs/specs/user.md §4 · user-R17 · user-R18 · user-R19
 * <p>
 * 제출된 이메일의 로그인 실패 기록에 걸어 대조한다({@link FailureThrottle}) — 계정이 없거나 쓰지 않으면 더미 해시와 대조하고, 128 코드
 * 포인트를 넘는 비밀번호는 대조 없이 실패로 센다.
 */
@Service
public class SignIn {

	private final AccountRepository accounts;

	private final PasswordCredentialRepository credentials;

	private final PasswordHashing hashing;

	private final FailureThrottle throttle;

	SignIn(AccountRepository accounts, PasswordCredentialRepository credentials, PasswordHashing hashing,
			FailureThrottle throttle) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.credentials = Objects.requireNonNull(credentials, "credentials");
		this.hashing = Objects.requireNonNull(hashing, "hashing");
		this.throttle = Objects.requireNonNull(throttle, "throttle");
	}

	/** 제출된 이메일 · 비밀번호로 한 번 로그인을 시도한다 */
	@Transactional
	public SignInOutcome attempt(String rawEmail, String rawPassword, Instant now) {
		Optional<EmailAddress> email = EmailAddress.parse(rawEmail);
		String password = PasswordPolicy.normalize(rawPassword);
		return switch (this.throttle.attempt(ThrottledAttempt.SIGN_IN, EmailDigest.ofSubmitted(rawEmail), now,
				() -> verify(email, password))) {
			case Throttled.Passed<UserId> passed -> new SignInOutcome.SignedIn(passed.value());
			case Throttled.Failed<UserId> failed -> new SignInOutcome.Rejected();
			case Throttled.Wait<UserId> wait -> new SignInOutcome.Wait(wait.retryAt());
		};
	}

	/** 맞는 비밀번호의 쓰는 계정 — 계정이 없거나 쓰지 않으면 더미 해시와 대조해 같은 계산을 한다(user-R17) */
	private Optional<UserId> verify(Optional<EmailAddress> email, String password) {
		if (!PasswordPolicy.comparable(password)) {
			return Optional.empty();
		}
		Optional<Account> account = email.flatMap(address -> this.accounts.findByEmail(address.value()))
			.filter(Account::active);
		Optional<PasswordCredential> credential = account
			.flatMap(found -> this.credentials.findById(found.id().value()));
		if (account.isEmpty() || credential.isEmpty()) {
			this.hashing.matchesNobody(password);
			return Optional.empty();
		}
		return this.hashing.matches(password, credential.get().passwordHash()) ? Optional.of(account.get().id())
				: Optional.empty();
	}

}
