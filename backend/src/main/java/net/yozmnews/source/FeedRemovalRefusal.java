package net.yozmnews.source;

/**
 * 피드를 숨기거나 지우지 않은 이유 — docs/specs/source.md §3
 */
public enum FeedRemovalRefusal {

	/** 프로필에 이 피드가 있다 — 숨기거나 지워도 프로필이 다시 더한다 */
	IN_PROFILE,
	/** 프로필을 얻지 못했다 — 프리셋이 사라졌거나 운영자 원문이 검증에 걸린다. 소스 단위로 숨기거나 지운다 */
	PROFILE_UNAVAILABLE

}
