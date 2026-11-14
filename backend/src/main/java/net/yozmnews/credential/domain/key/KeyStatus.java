package net.yozmnews.credential.domain.key;

/**
 * 키의 상태 — docs/specs/credential.md §4 · credential-R14
 */
public enum KeyStatus {

	/** 쓸 수 있다 — 등록된 키는 이 상태로 시작하고, 무효였던 키가 성공하면 돌아온다 */
	VALID,

	/** 공급자가 무효 · 권한 없음으로 답했다 */
	INVALID

}
