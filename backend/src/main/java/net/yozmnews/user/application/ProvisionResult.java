package net.yozmnews.user.application;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.secret.RecoveryCode;

/**
 * 계정 만들기의 결과 — 만들었음 · 이미 있는 이메일. docs/specs/user.md §4
 */
sealed interface ProvisionResult {

	/**
	 * 만들었다.
	 * @param recoveryCode 만들 때 준 복구 코드 — 강제 변경 계정이면 비어 있다(비밀번호를 바꾼 직후에 준다)
	 */
	record Created(UserId user, Optional<RecoveryCode> recoveryCode) implements ProvisionResult {

		public Created {
			Objects.requireNonNull(user, "user");
			Objects.requireNonNull(recoveryCode, "recoveryCode");
		}

	}

	/** 같은 이메일의 계정이 있어 아무것도 쓰지 않았다 */
	record EmailTaken() implements ProvisionResult {

	}

}
