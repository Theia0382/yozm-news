package net.yozmnews.user;

/**
 * 스토리라인 구독 이력 행의 동작 — 구독을 더했는가 뺐는가. docs/specs/user.md §3 · user-R52
 */
public enum StorylineSubscriptionAction {

	/** 구독을 더했다 */
	ADD,

	/** 구독을 뺐다 */
	REMOVE

}
