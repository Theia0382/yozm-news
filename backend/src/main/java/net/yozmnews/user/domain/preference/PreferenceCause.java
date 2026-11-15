package net.yozmnews.user.domain.preference;

/**
 * 선호 이력의 원인 — 관심 신호는 {@link #USER} 행만이다 — docs/specs/user.md §5 · user-R45
 */
public enum PreferenceCause {

	/** 사용자가 바꿨다 */
	USER,

	/** 계정을 만들 때 넣은 기본값 */
	ONBOARDING,

	/** 태그 체계의 활성 전환을 따라 고쳤다 */
	TAG_SET_CHANGE

}
