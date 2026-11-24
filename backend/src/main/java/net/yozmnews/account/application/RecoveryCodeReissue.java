package net.yozmnews.account.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;

import net.yozmnews.account.domain.credential.PasswordCredential;
import net.yozmnews.account.domain.recovery.RecoveryCodeRepository;
import net.yozmnews.account.domain.secret.RecoveryCode;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복구 코드 재발급 — 세션 사용자가 현재 비밀번호를 확인하고 새 복구 코드를 받는다. 앞 코드는 무효가 된다. docs/specs/account.md §4 · account-R23
 */
@Service
public class RecoveryCodeReissue {

	private final CurrentPasswordCheck check;

	private final RecoveryCodeRepository recoveryCodes;

	private final SecureRandom random = new SecureRandom();

	RecoveryCodeReissue(CurrentPasswordCheck check, RecoveryCodeRepository recoveryCodes) {
		this.check = Objects.requireNonNull(check, "check");
		this.recoveryCodes = Objects.requireNonNull(recoveryCodes, "recoveryCodes");
	}

	/** 현재 비밀번호를 확인하고 복구 코드를 새로 준다 */
	@Transactional
	public ReissueOutcome reissue(UserId user, String currentPassword, Instant now) {
		return switch (this.check.check(user, currentPassword, now)) {
			case Throttled.Failed<PasswordCredential> failed -> new ReissueOutcome.Rejected();
			case Throttled.Wait<PasswordCredential> wait -> new ReissueOutcome.Wait(wait.retryAt());
			case Throttled.Passed<PasswordCredential> passed -> {
				RecoveryCode code = RecoveryCode.generate(this.random);
				this.recoveryCodes.replace(user, code.digest(), now);
				yield new ReissueOutcome.Reissued(code);
			}
		};
	}

}
