package net.yozmnews.source.domain.registry;

/**
 * 소스 · 피드를 켜지 않은 이유 — 피드를 수집하지 않는 이유이기도 하다.
 */
public enum ActivationRefusal {

	/** 프로필을 얻지 못했다 — 프리셋이 사라졌거나 운영자 원문이 검증에 걸린다 */
	PROFILE_UNAVAILABLE,
	/** 프로필에 이용조건 검토일이 없다 */
	TERMS_NOT_REVIEWED,
	/** 프로필에 이 피드가 없다 — 프리셋 배포가 피드를 뺐다 */
	FEED_NOT_IN_PROFILE

}
