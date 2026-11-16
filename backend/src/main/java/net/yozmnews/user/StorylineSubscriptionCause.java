package net.yozmnews.user;

/**
 * 스토리라인 구독 이력 행의 원인 — 닫힌 집합의 정본. 관심 신호는 원인이 {@link #USER} 인 행만이다. docs/specs/user.md §3 · user-R52 ·
 * user-R53
 */
public enum StorylineSubscriptionCause {

	/** 사용자가 구독하거나 해제했다 */
	USER,

	/** 구독한 스토리라인이 다른 스토리라인에 합쳐져 남는 스토리라인으로 옮겼다 */
	STORYLINE_MERGED,

	/** 구독한 스토리라인이 지워져(풀기 포함) 구독에서 뺐다 */
	STORYLINE_REMOVED

}
