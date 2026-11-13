package net.yozmnews.user;

/**
 * 초대의 상태 — 판정 정본은 초대 aggregate 이고, 운영자 목록 · 취소 결과가 이 열거를 낸다. docs/specs/user.md §3.
 * <p>
 * 우선순위는 쓴 것 · 취소한 것 · 만료한 것 · 유효한 것 순이다 — 쓴 초대는 만료 시각이 지나도 쓴 것이다.
 */
public enum InvitationState {

	/** 가입에 쓸 수 있다 */
	VALID,

	/** 이미 가입에 썼다 */
	USED,

	/** 만료됐다 */
	EXPIRED,

	/** 운영자가 취소했다 */
	REVOKED

}
