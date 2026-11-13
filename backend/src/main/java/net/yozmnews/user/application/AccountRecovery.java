package net.yozmnews.user.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.user.PasswordProblem;
import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.credential.PasswordCredential;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import net.yozmnews.user.domain.notice.PasswordChangeMethod;
import net.yozmnews.user.domain.recovery.RecoveryCodeRepository;
import net.yozmnews.user.domain.secret.RecoveryCode;
import net.yozmnews.user.domain.secret.SecretDigest;
import net.yozmnews.user.domain.throttle.EmailDigest;
import net.yozmnews.user.domain.throttle.ThrottledAttempt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복구 코드로 비밀번호 재설정 — 로그인하지 않은 사람이 이메일 · 복구 코드 · 새 비밀번호를 낸다. 한 번의 시도가 트랜잭션 하나다.
 * docs/specs/user.md §4 · user-R30
 * <p>
 * 새 비밀번호 규칙(이유를 알려도 계정 유무가 드러나지 않는다) → 제출된 이메일의 복구 실패 기록에 걸어 코드를 대조한다 — 계정이 없거나 쓰지 않거나
 * 코드가 없거나 읽을 수 없으면 더미 다이제스트와 같은 대조를 하고 거절한다(로그인과 같은 응답 규칙). 맞으면 비밀번호를 바꾸고(강제 변경도
 * 풀린다) 복구 코드를 새로 주며, 비밀번호 알림을 남기고, 마지막으로 이 사용자의 세션을 모두 지운다.
 */
@Service
public class AccountRecovery {

	private final AccountRepository accounts;

	private final PasswordCredentialRepository credentials;

	private final RecoveryCodeRepository recoveryCodes;

	private final PasswordHashing hashing;

	private final FailureThrottle throttle;

	private final PasswordChangedNotices notices;

	private final OtherSessions sessions;

	private final SecureRandom random = new SecureRandom();

	/** 아무 계정의 것도 아닌 다이제스트 — 대조할 코드가 없을 때도 같은 대조를 한다 */
	private final SecretDigest nobody = RecoveryCode.generate(this.random).digest();

	AccountRecovery(AccountRepository accounts, PasswordCredentialRepository credentials,
			RecoveryCodeRepository recoveryCodes, PasswordHashing hashing, FailureThrottle throttle,
			PasswordChangedNotices notices, OtherSessions sessions) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.credentials = Objects.requireNonNull(credentials, "credentials");
		this.recoveryCodes = Objects.requireNonNull(recoveryCodes, "recoveryCodes");
		this.hashing = Objects.requireNonNull(hashing, "hashing");
		this.throttle = Objects.requireNonNull(throttle, "throttle");
		this.notices = Objects.requireNonNull(notices, "notices");
		this.sessions = Objects.requireNonNull(sessions, "sessions");
	}

	/** 복구 코드로 비밀번호를 재설정한다 */
	@Transactional
	public RecoveryOutcome reset(String rawEmail, String rawCode, String newPassword, Instant now) {
		String normalized = PasswordPolicy.normalize(newPassword);
		Optional<PasswordProblem> problem = PasswordPolicy.check(normalized);
		if (problem.isPresent()) {
			return new RecoveryOutcome.PasswordRejected(problem.get());
		}
		Optional<EmailAddress> email = EmailAddress.parse(rawEmail);
		return switch (this.throttle.attempt(ThrottledAttempt.RECOVERY, EmailDigest.ofSubmitted(rawEmail), now,
				() -> verify(email, RecoveryCode.parse(rawCode)))) {
			case Throttled.Failed<UserId> failed -> new RecoveryOutcome.Rejected();
			case Throttled.Wait<UserId> wait -> new RecoveryOutcome.Wait(wait.retryAt());
			case Throttled.Passed<UserId> passed -> reset(passed.value(), normalized, now);
		};
	}

	/** 맞는 복구 코드의 쓰는 계정 — 대조할 코드가 없으면 더미 다이제스트와 대조해 같은 계산을 한다 */
	private Optional<UserId> verify(Optional<EmailAddress> email, Optional<RecoveryCode> code) {
		Optional<Account> account = email.flatMap(address -> this.accounts.findByEmail(address.value()))
			.filter(Account::active);
		Optional<SecretDigest> stored = account.flatMap(found -> this.recoveryCodes.find(found.id()));
		SecretDigest presented = code.map(RecoveryCode::digest).orElse(this.nobody);
		if (account.isEmpty() || stored.isEmpty() || code.isEmpty()) {
			this.nobody.matches(presented);
			return Optional.empty();
		}
		return stored.get().matches(presented) ? Optional.of(account.get().id()) : Optional.empty();
	}

	private RecoveryOutcome reset(UserId user, String normalized, Instant now) {
		this.credentials.lockForUpdate(user.value());
		PasswordCredential credential = this.credentials.findById(user.value())
			.orElseThrow(() -> new IllegalStateException("복구한 계정의 자격이 없다: " + user.value()));
		this.credentials.save(credential.changed(this.hashing.hash(normalized), now));
		RecoveryCode code = RecoveryCode.generate(this.random);
		this.recoveryCodes.replace(user, code.digest(), now);
		this.notices.record(user, PasswordChangeMethod.RECOVERY, now);
		this.sessions.endAll(user);
		return new RecoveryOutcome.Reset(user, code);
	}

}
