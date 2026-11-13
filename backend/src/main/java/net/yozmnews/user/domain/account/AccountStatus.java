package net.yozmnews.user.domain.account;

/**
 * 계정의 상태 — 로그인 · 세션은 {@link #ACTIVE} 만 받는다. docs/specs/user.md §4 · user-R7 · user-R17
 */
public enum AccountStatus {

	/** 쓰는 계정 */
	ACTIVE,

	/** 정지된 계정 */
	SUSPENDED,

	/** 탈퇴한 계정 */
	DELETED

}
