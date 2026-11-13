package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import net.yozmnews.user.domain.credential.PasswordProblem;
import net.yozmnews.user.domain.throttle.EmailDigest;
import net.yozmnews.user.domain.throttle.FailureRecord;
import net.yozmnews.user.domain.throttle.LoginFailures;
import net.yozmnews.user.domain.throttle.LoginThrottle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 유스케이스 — 한 번의 시도가 트랜잭션 하나다. docs/specs/user.md §4 · user-R17 · user-R18 · user-R19
 * <p>
 * 제출된 이메일의 실패 기록을 잠그고 → 기다릴 때면 대조 없이 대기 → 아니면 대조(계정이 없거나 쓰지 않으면 더미 해시, 128 코드 포인트를 넘는
 * 비밀번호는 대조 없이 실패) → 성공이면 기록을 지우고 실패면 기록을 올린다. 잠금이 대조 동안 이어져 같은 이메일의 동시 시도는 줄을 선다.
 */
@Service
public class SignIn {

	private final AccountRepository accounts;

	private final PasswordCredentialRepository credentials;

	private final PasswordHashing hashing;

	private final LoginFailures failures;

	SignIn(AccountRepository accounts, PasswordCredentialRepository credentials, PasswordHashing hashing,
			LoginFailures failures) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.credentials = Objects.requireNonNull(credentials, "credentials");
		this.hashing = Objects.requireNonNull(hashing, "hashing");
		this.failures = Objects.requireNonNull(failures, "failures");
	}

	/** 제출된 이메일 · 비밀번호로 한 번 로그인을 시도한다 */
	@Transactional
	public SignInOutcome attempt(String rawEmail, String rawPassword, Instant now) {
		Optional<EmailAddress> email = EmailAddress.parse(rawEmail);
		EmailDigest digest = EmailDigest.of(email.map(EmailAddress::value).orElseGet(() -> EmailAddress.normalize(rawEmail)));
		FailureRecord record = this.failures.lock(digest);
		Optional<Instant> retryAt = LoginThrottle.retryAt(record, now);
		if (retryAt.isPresent()) {
			return new SignInOutcome.Wait(retryAt.get());
		}
		Optional<UserId> user = verify(email, PasswordPolicy.normalize(rawPassword));
		if (user.isPresent()) {
			this.failures.forget(digest);
			return new SignInOutcome.SignedIn(user.get());
		}
		this.failures.record(digest, LoginThrottle.failed(record, now));
		return new SignInOutcome.Rejected();
	}

	/** 맞는 비밀번호의 쓰는 계정 — 계정이 없거나 쓰지 않으면 더미 해시와 대조해 같은 계산을 한다(user-R17) */
	private Optional<UserId> verify(Optional<EmailAddress> email, String password) {
		if (PasswordPolicy.check(password).filter(problem -> problem == PasswordProblem.TOO_LONG).isPresent()) {
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
