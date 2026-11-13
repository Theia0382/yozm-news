package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.user.PasswordProblem;
import net.yozmnews.user.domain.secret.RecoveryCode;

/**
 * 비밀번호 변경의 결과 — 바꿈 · 거절 · 대기 · 새 비밀번호 규칙 위반. docs/specs/user.md §4
 */
public sealed interface PasswordChangeOutcome {

	/**
	 * 바꿨다.
	 * @param recoveryCode 강제 변경이 풀리며 새로 준 복구 코드 — 강제 변경이 아니었으면 비어 있다
	 */
	record Changed(Optional<RecoveryCode> recoveryCode) implements PasswordChangeOutcome {

		public Changed {
			Objects.requireNonNull(recoveryCode, "recoveryCode");
		}

	}

	/** 현재 비밀번호가 틀렸다 */
	record Rejected() implements PasswordChangeOutcome {

	}

	/** 연속 실패로 기다려야 한다 — 대조하지 않았다 */
	record Wait(Instant retryAt) implements PasswordChangeOutcome {

		public Wait {
			Objects.requireNonNull(retryAt, "retryAt");
		}

	}

	/** 새 비밀번호가 규칙을 어긴다 — 현재 비밀번호를 대조하지 않았다 */
	record PasswordRejected(PasswordProblem problem) implements PasswordChangeOutcome {

		public PasswordRejected {
			Objects.requireNonNull(problem, "problem");
		}

	}

}
