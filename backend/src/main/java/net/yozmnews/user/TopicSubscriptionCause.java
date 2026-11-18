package net.yozmnews.user;

/**
 * 주제 구독 이력 행의 원인 — 선호 이력의 저장 원인을 공개 조회의 값으로 옮긴 것이다. 관심 신호는 원인이 {@link #USER} 인 행만이다.
 * docs/specs/user.md §3 · user-R45 · user-R58
 */
public enum TopicSubscriptionCause {

	/** 사용자가 바꿨다 */
	USER,

	/** 계정을 만들 때 넣은 기본값 */
	ONBOARDING,

	/** 태그 체계의 활성 전환을 따라 고쳤다 */
	TAG_SET_CHANGE

}
