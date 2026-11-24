package net.yozmnews.account.domain.session;

import org.jspecify.annotations.Nullable;

/**
 * 운영자 경로의 인가 판정 — 정본 · docs/specs/user.md §4 · user-R1
 * <p>
 * LLM 키는 묻지 않는다 — 운영자 경로는 키 관문 밖이다.
 */
public enum AdminAccess {

	/** 연다 */
	OPEN,

	/** 운영자 토큰 인증도 세션 사용자도 없다 */
	UNAUTHENTICATED,

	/** 세션 사용자가 비밀번호를 바꿔야 한다 */
	PASSWORD_CHANGE_REQUIRED,

	/** 세션 사용자가 운영자가 아니다 */
	FORBIDDEN;

	/**
	 * 운영자 토큰 인증, 세션 사용자, 강제 변경, 역할의 순서로 판정한다. 강제 변경 계정은 역할 권한이 없으므로 역할보다 강제 변경을 먼저 본다.
	 * @param operatorToken 운영자 토큰으로 인증한 요청인가
	 * @param mustChangePassword 세션 사용자가 비밀번호를 바꿔야 하는가 — 세션 사용자가 없으면 null
	 * @param admin 세션 사용자가 운영자인가 — 세션 사용자가 없거나 강제 변경이면 보지 않는다
	 */
	public static AdminAccess decide(boolean operatorToken, @Nullable Boolean mustChangePassword, boolean admin) {
		if (operatorToken) {
			return OPEN;
		}
		if (mustChangePassword == null) {
			return UNAUTHENTICATED;
		}
		if (mustChangePassword) {
			return PASSWORD_CHANGE_REQUIRED;
		}
		return admin ? OPEN : FORBIDDEN;
	}

}
