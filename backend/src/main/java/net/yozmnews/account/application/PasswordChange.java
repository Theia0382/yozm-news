package net.yozmnews.account.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.account.PasswordChangeMethod;
import net.yozmnews.account.PasswordChanged;
import net.yozmnews.account.PasswordProblem;
import net.yozmnews.account.domain.credential.PasswordCredential;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.account.domain.credential.PasswordPolicy;
import net.yozmnews.account.domain.recovery.RecoveryCodeRepository;
import net.yozmnews.account.domain.secret.RecoveryCode;
import net.yozmnews.platform.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 변경 유스케이스 — 세션 사용자가 현재 비밀번호를 확인하고 새 비밀번호로 바꾼다. 한 번의 변경이 트랜잭션 하나다. docs/specs/account.md §4
 * <p>
 * 새 비밀번호 규칙 → 현재 비밀번호 확인(로그인 실패 기록 · 자격 잠금) → 자격 변경. 강제 변경 중이었으면 강제 변경이 풀리고 복구 코드를 새로
 * 주며 비밀번호 바뀜을 알리지 않는다(본인이 막 만든 계정의 첫 단계). 그 밖이면 같은 트랜잭션에서 {@link PasswordChanged} 를 낸다(account-R36).
 * 마지막으로 이 세션을 뺀 이 사용자의 세션을 지운다.
 */
@Service
public class PasswordChange {

	private final CurrentPasswordCheck check;

	private final PasswordCredentialRepository credentials;

	private final RecoveryCodeRepository recoveryCodes;

	private final PasswordHashing hashing;

	private final ApplicationEventPublisher events;

	private final OtherSessions sessions;

	private final SecureRandom random = new SecureRandom();

	PasswordChange(CurrentPasswordCheck check, PasswordCredentialRepository credentials,
			RecoveryCodeRepository recoveryCodes, PasswordHashing hashing, ApplicationEventPublisher events,
			OtherSessions sessions) {
		this.check = Objects.requireNonNull(check, "check");
		this.credentials = Objects.requireNonNull(credentials, "credentials");
		this.recoveryCodes = Objects.requireNonNull(recoveryCodes, "recoveryCodes");
		this.hashing = Objects.requireNonNull(hashing, "hashing");
		this.events = Objects.requireNonNull(events, "events");
		this.sessions = Objects.requireNonNull(sessions, "sessions");
	}

	/**
	 * 비밀번호를 바꾼다.
	 * @param keepSessionId 남길 세션 — 세션 저장소에 지금 있는 이 요청의 세션 ID(요청 안에서 ID 를 바꾸기 전)
	 */
	@Transactional
	public PasswordChangeOutcome change(UserId user, String currentPassword, String newPassword, String keepSessionId,
			Instant now) {
		String normalized = PasswordPolicy.normalize(newPassword);
		Optional<PasswordProblem> problem = PasswordPolicy.check(normalized);
		if (problem.isPresent()) {
			return new PasswordChangeOutcome.PasswordRejected(problem.get());
		}
		return switch (this.check.check(user, currentPassword, now)) {
			case Throttled.Failed<PasswordCredential> failed -> new PasswordChangeOutcome.Rejected();
			case Throttled.Wait<PasswordCredential> wait -> new PasswordChangeOutcome.Wait(wait.retryAt());
			case Throttled.Passed<PasswordCredential> passed -> changed(user, passed.value(), normalized,
					keepSessionId, now);
		};
	}

	private PasswordChangeOutcome changed(UserId user, PasswordCredential credential, String normalized,
			String keepSessionId, Instant now) {
		this.credentials.save(credential.changed(this.hashing.hash(normalized), now));
		Optional<RecoveryCode> recoveryCode = Optional.empty();
		if (credential.mustChange()) {
			RecoveryCode code = RecoveryCode.generate(this.random);
			this.recoveryCodes.replace(user, code.digest(), now);
			recoveryCode = Optional.of(code);
		}
		else {
			this.events.publishEvent(new PasswordChanged(user, PasswordChangeMethod.CHANGE, now));
		}
		this.sessions.endAllExcept(user, keepSessionId);
		return new PasswordChangeOutcome.Changed(recoveryCode);
	}

}
