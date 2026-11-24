package net.yozmnews.account.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.account.domain.account.Account;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.account.EmailAddress;
import net.yozmnews.account.domain.credential.PasswordCredential;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.account.domain.credential.PasswordPolicy;
import net.yozmnews.account.domain.throttle.EmailDigest;
import net.yozmnews.account.domain.throttle.ThrottledAttempt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 유스케이스 — 한 번의 시도가 트랜잭션 하나이고, 성공하면 세션을 연 뒤 자격을 다시 확인한다. docs/specs/account.md §4 · account-R17 ·
 * account-R18 · account-R19
 * <p>
 * 제출된 이메일의 로그인 실패 기록에 걸어 대조한다({@link FailureThrottle}) — 계정이 없거나 쓰지 않으면 더미 해시와 대조하고, 128 코드
 * 포인트를 넘는 비밀번호는 대조 없이 실패로 센다. 대조는 자격 행을 잠그지 않으므로, 대조한 자격의 버전을 결과에 싣고 세션을 저장한 뒤
 * {@link #stillCurrent} 로 다시 본다.
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
			case Throttled.Passed<SignInOutcome.SignedIn> passed -> passed.value();
			case Throttled.Failed<SignInOutcome.SignedIn> failed -> new SignInOutcome.Rejected();
			case Throttled.Wait<SignInOutcome.SignedIn> wait -> new SignInOutcome.Wait(wait.retryAt());
		};
	}

	/**
	 * 로그인으로 연 세션이 대조한 자격 그대로인지 확인한다 — 세션을 세션 저장소에 저장한 뒤 부른다. 자격 행을 잠가, 커밋 전인 자격 변경이
	 * 있으면 그 커밋을 기다린 뒤 본다.
	 * @return 대조 뒤 자격이 바뀌었거나 없어졌으면 거짓 — 부르는 쪽이 연 세션을 거둔다
	 */
	@Transactional
	public boolean stillCurrent(SignInOutcome.SignedIn signedIn) {
		long user = signedIn.userId().value();
		this.credentials.lockForUpdate(user);
		return this.credentials.findById(user)
			.map(credential -> credential.version() == signedIn.credentialVersion())
			.orElse(false);
	}

	/** 맞는 비밀번호의 쓰는 계정과 대조한 자격의 버전 — 계정이 없거나 쓰지 않으면 더미 해시와 대조해 같은 계산을 한다(account-R17) */
	private Optional<SignInOutcome.SignedIn> verify(Optional<EmailAddress> email, String password) {
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
		return this.hashing.matches(password, credential.get().passwordHash())
				? Optional.of(new SignInOutcome.SignedIn(account.get().id(), credential.get().version()))
				: Optional.empty();
	}

}
