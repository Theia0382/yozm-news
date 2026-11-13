package net.yozmnews.user.adapter.in.web;

import java.time.Instant;

import net.yozmnews.user.PasswordProblem;
import net.yozmnews.user.application.PasswordChangeOutcome;
import net.yozmnews.user.domain.secret.RecoveryCode;
import org.jspecify.annotations.Nullable;

/**
 * 비밀번호 변경 결과 — 판정 결과 200. docs/specs/user.md §3
 *
 * @param result 바꿈 · 거절 · 대기 · 새 비밀번호 규칙 위반
 * @param recoveryCode 강제 변경이 풀리며 새로 준 복구 코드(4자씩 {@code -}) — 그 밖 null
 * @param retryAfterSeconds 대기면 남은 초(올림), 그 밖 null
 * @param passwordProblem 새 비밀번호가 어긴 규칙, 그 밖 null
 */
record PasswordChangeResponse(Result result, @Nullable String recoveryCode, @Nullable Long retryAfterSeconds,
		@Nullable PasswordProblem passwordProblem) {

	static PasswordChangeResponse of(PasswordChangeOutcome outcome, Instant now) {
		return switch (outcome) {
			case PasswordChangeOutcome.Changed changed -> new PasswordChangeResponse(Result.CHANGED,
					changed.recoveryCode().map(RecoveryCode::display).orElse(null), null, null);
			case PasswordChangeOutcome.Rejected rejected -> new PasswordChangeResponse(Result.REJECTED, null, null, null);
			case PasswordChangeOutcome.Wait wait -> new PasswordChangeResponse(Result.WAIT, null,
					RetryAfter.seconds(wait.retryAt(), now), null);
			case PasswordChangeOutcome.PasswordRejected rejected -> new PasswordChangeResponse(Result.PASSWORD_REJECTED,
					null, null, rejected.problem());
		};
	}

	@Override
	public String toString() {
		return "PasswordChangeResponse[result=" + this.result + "]";
	}

	/** 비밀번호 변경 결과의 판별자 */
	enum Result {

		/** 바꿨다 */
		CHANGED,

		/** 현재 비밀번호가 맞지 않는다 */
		REJECTED,

		/** 연속 실패로 기다려야 한다 */
		WAIT,

		/** 새 비밀번호가 규칙을 어긴다 */
		PASSWORD_REJECTED

	}

}
