package net.yozmnews.user.domain.notice;

/**
 * 비밀번호를 바꾼 방법 — 비밀번호 알림의 내용. docs/specs/user.md §4
 */
public enum PasswordChangeMethod {

	/** 세션 사용자가 현재 비밀번호를 확인하고 바꿨다 */
	CHANGE,

	/** 복구 코드로 재설정했다 */
	RECOVERY

}
