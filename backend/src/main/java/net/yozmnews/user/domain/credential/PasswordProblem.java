package net.yozmnews.user.domain.credential;

/**
 * 비밀번호 규칙을 어긴 까닭 — docs/specs/user.md §4 · user-R14
 */
public enum PasswordProblem {

	/** 정규화한 뒤 최소 길이보다 짧다 */
	TOO_SHORT,

	/** 정규화한 뒤 최대 길이보다 길다 */
	TOO_LONG

}
