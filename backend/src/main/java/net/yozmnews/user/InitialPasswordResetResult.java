package net.yozmnews.user;

import java.util.Objects;

/**
 * 초기 비밀번호 다시 정하기의 결과 — docs/specs/user.md §3
 */
public sealed interface InitialPasswordResetResult {

	/** 다시 정했다 — 그 계정의 세션은 모두 끝났다 */
	record Reset() implements InitialPasswordResetResult {

	}

	/** 본인이 이미 비밀번호를 바꾼 계정이다 — 바꾸지 않았다 */
	record NotPending() implements InitialPasswordResetResult {

	}

	/** 그 이메일의 쓰는 계정이 없다 */
	record NoAccount() implements InitialPasswordResetResult {

	}

	/** 새 초기 비밀번호가 규칙을 어긴다 */
	record PasswordRejected(PasswordProblem problem) implements InitialPasswordResetResult {

		public PasswordRejected {
			Objects.requireNonNull(problem, "problem");
		}

	}

}
