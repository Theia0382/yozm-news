package net.yozmnews.user.adapter.in.web;

import java.time.Instant;

import net.yozmnews.user.PasswordProblem;
import net.yozmnews.user.application.RecoveryOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 복구 코드 재설정 결과 — 판정 결과 200. 거절은 계정 · 코드 유무와 무관하게 같은 본문이다. docs/specs/user.md §3
 *
 * @param result 재설정 · 거절 · 대기 · 새 비밀번호 규칙 위반
 * @param recoveryCode 새 복구 코드(4자씩 {@code -}) — 재설정일 때만, 그 밖 null
 * @param retryAfterSeconds 대기면 남은 초(올림), 그 밖 null
 * @param passwordProblem 새 비밀번호가 어긴 규칙, 그 밖 null
 */
record AccountRecoveryResponse(Result result, @Nullable String recoveryCode, @Nullable Long retryAfterSeconds,
		@Nullable PasswordProblem passwordProblem) {

	static AccountRecoveryResponse of(RecoveryOutcome outcome, Instant now) {
		return switch (outcome) {
			case RecoveryOutcome.Reset reset -> new AccountRecoveryResponse(Result.RESET,
					reset.recoveryCode().display(), null, null);
			case RecoveryOutcome.Rejected rejected -> new AccountRecoveryResponse(Result.REJECTED, null, null, null);
			case RecoveryOutcome.Wait wait -> new AccountRecoveryResponse(Result.WAIT, null,
					RetryAfter.seconds(wait.retryAt(), now), null);
			case RecoveryOutcome.PasswordRejected rejected -> new AccountRecoveryResponse(Result.PASSWORD_REJECTED, null,
					null, rejected.problem());
		};
	}

	@Override
	public String toString() {
		return "AccountRecoveryResponse[result=" + this.result + "]";
	}

	/** 복구 코드 재설정 결과의 판별자 */
	enum Result {

		/** 재설정했다 — 새 세션으로 로그인했다 */
		RESET,

		/** 이메일 또는 복구 코드가 맞지 않는다 */
		REJECTED,

		/** 연속 실패로 기다려야 한다 */
		WAIT,

		/** 새 비밀번호가 규칙을 어긴다 */
		PASSWORD_REJECTED

	}

}
