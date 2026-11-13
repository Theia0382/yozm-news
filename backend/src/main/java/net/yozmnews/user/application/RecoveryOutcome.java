package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.user.PasswordProblem;
import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.secret.RecoveryCode;

/**
 * 복구 코드 재설정의 결과 — 재설정 · 거절 · 대기 · 새 비밀번호 규칙 위반. 거절은 계정 · 코드 유무를 가르지 않는다. docs/specs/user.md §4
 */
public sealed interface RecoveryOutcome {

	/**
	 * 재설정했다 — 앞 코드는 무효가 됐다.
	 * @param recoveryCode 새로 준 복구 코드
	 */
	record Reset(UserId user, RecoveryCode recoveryCode) implements RecoveryOutcome {

		public Reset {
			Objects.requireNonNull(user, "user");
			Objects.requireNonNull(recoveryCode, "recoveryCode");
		}

	}

	/** 계정이 없거나 쓰지 않거나 코드가 틀렸다 — 셋을 가르지 않는다 */
	record Rejected() implements RecoveryOutcome {

	}

	/** 연속 실패로 기다려야 한다 — 대조하지 않았다 */
	record Wait(Instant retryAt) implements RecoveryOutcome {

		public Wait {
			Objects.requireNonNull(retryAt, "retryAt");
		}

	}

	/** 새 비밀번호가 규칙을 어긴다 — 코드를 대조하지 않았다 */
	record PasswordRejected(PasswordProblem problem) implements RecoveryOutcome {

		public PasswordRejected {
			Objects.requireNonNull(problem, "problem");
		}

	}

}
