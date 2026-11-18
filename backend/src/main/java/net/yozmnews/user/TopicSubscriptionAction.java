package net.yozmnews.user;

/**
 * 주제 구독 이력 행의 더함 · 뺌 — {@link TopicSubscriptionChange} 의 값. docs/specs/user.md §3 · user-R58
 */
public enum TopicSubscriptionAction {

	/** 구독했다 */
	ADD,

	/** 구독을 뺐다 */
	REMOVE

}
