package net.yozmnews.user;

/**
 * 비밀번호를 바꾼 방법 — 비밀번호 바뀜 이벤트와 비밀번호 알림의 내용이 싣는다. docs/specs/account.md §3
 */
public enum PasswordChangeMethod {

	/** 세션 사용자가 현재 비밀번호를 확인하고 바꿨다 */
	CHANGE,

	/** 복구 코드로 재설정했다 */
	RECOVERY

}
