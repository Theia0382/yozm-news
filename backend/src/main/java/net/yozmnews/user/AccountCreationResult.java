package net.yozmnews.user;

import java.util.Objects;

/**
 * 운영자 계정 생성의 결과 — docs/specs/user.md §3
 */
public sealed interface AccountCreationResult {

	/** 만들었다 */
	record Created(UserId user) implements AccountCreationResult {

		public Created {
			Objects.requireNonNull(user, "user");
		}

	}

	/** 같은 이메일의 계정이 있다 */
	record EmailTaken() implements AccountCreationResult {

	}

	/** 이메일로 읽을 수 없다 */
	record EmailMalformed() implements AccountCreationResult {

	}

	/** 표시 이름이 비었거나 너무 길다 */
	record DisplayNameMalformed() implements AccountCreationResult {

	}

	/** 초기 비밀번호가 규칙을 어긴다 */
	record PasswordRejected(PasswordProblem problem) implements AccountCreationResult {

		public PasswordRejected {
			Objects.requireNonNull(problem, "problem");
		}

	}

}
