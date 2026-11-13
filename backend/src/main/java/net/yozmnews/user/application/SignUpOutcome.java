package net.yozmnews.user.application;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.user.InvitationState;
import net.yozmnews.user.PasswordProblem;
import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.secret.RecoveryCode;

/**
 * 가입(공개 가입 · 초대 가입)의 결과 — docs/specs/user.md §4
 */
public sealed interface SignUpOutcome {

	/** 가입했다 — 복구 코드는 여기서 한 번만 나간다 */
	record SignedUp(UserId user, RecoveryCode recoveryCode) implements SignUpOutcome {

		public SignedUp {
			Objects.requireNonNull(user, "user");
			Objects.requireNonNull(recoveryCode, "recoveryCode");
		}

	}

	/** 공개 가입이 닫혔다 */
	record Closed() implements SignUpOutcome {

	}

	/**
	 * 초대를 쓸 수 없다.
	 * @param state 초대의 상태 — 읽을 수 없거나 없는 토큰이면 비어 있다
	 */
	record InvitationUnusable(Optional<InvitationState> state) implements SignUpOutcome {

		public InvitationUnusable {
			Objects.requireNonNull(state, "state");
		}

	}

	/** 같은 이메일의 계정이 있다 — 응답은 "가입할 수 없는 이메일"이다 */
	record EmailUnavailable() implements SignUpOutcome {

	}

	/** 이메일로 읽을 수 없다 */
	record EmailMalformed() implements SignUpOutcome {

	}

	/** 표시 이름이 비었거나 너무 길다 */
	record DisplayNameMalformed() implements SignUpOutcome {

	}

	/** 비밀번호가 규칙을 어긴다 */
	record PasswordRejected(PasswordProblem problem) implements SignUpOutcome {

		public PasswordRejected {
			Objects.requireNonNull(problem, "problem");
		}

	}

}
