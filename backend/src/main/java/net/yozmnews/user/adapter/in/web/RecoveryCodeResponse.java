package net.yozmnews.user.adapter.in.web;

import java.time.Instant;

import net.yozmnews.user.application.ReissueOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 복구 코드 재발급 결과 — 판정 결과 200. docs/specs/user.md §3
 *
 * @param result 재발급 · 거절 · 대기
 * @param recoveryCode 새 복구 코드(4자씩 {@code -}) — 재발급일 때만, 그 밖 null
 * @param retryAfterSeconds 대기면 남은 초(올림), 그 밖 null
 */
record RecoveryCodeResponse(Result result, @Nullable String recoveryCode, @Nullable Long retryAfterSeconds) {

	static RecoveryCodeResponse of(ReissueOutcome outcome, Instant now) {
		return switch (outcome) {
			case ReissueOutcome.Reissued reissued -> new RecoveryCodeResponse(Result.ISSUED,
					reissued.recoveryCode().display(), null);
			case ReissueOutcome.Rejected rejected -> new RecoveryCodeResponse(Result.REJECTED, null, null);
			case ReissueOutcome.Wait wait -> new RecoveryCodeResponse(Result.WAIT, null,
					RetryAfter.seconds(wait.retryAt(), now));
		};
	}

	@Override
	public String toString() {
		return "RecoveryCodeResponse[result=" + this.result + "]";
	}

	/** 복구 코드 재발급 결과의 판별자 */
	enum Result {

		/** 새 코드를 줬다 */
		ISSUED,

		/** 현재 비밀번호가 맞지 않는다 */
		REJECTED,

		/** 연속 실패로 기다려야 한다 */
		WAIT

	}

}
