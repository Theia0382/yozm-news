package net.yozmnews.user;

/**
 * 비밀번호 규칙을 어긴 까닭 — docs/specs/user.md §3 · user-R14. 운영자 계정 생성의 거부 이유로 {@code admin} 이 읽는다
 */
public enum PasswordProblem {

	/** 정규화한 뒤 최소 길이보다 짧다 */
	TOO_SHORT,

	/** 정규화한 뒤 최대 길이보다 길다 */
	TOO_LONG

}
