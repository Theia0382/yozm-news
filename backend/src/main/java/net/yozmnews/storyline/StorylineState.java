package net.yozmnews.storyline;

/**
 * 스토리라인의 상태 — 저장하지 않고 조회 때 마지막 기사 시각으로 정한다 · docs/specs/storyline.md §3 · storyline-R16.
 */
public enum StorylineState {

	/** 마지막 기사 뒤 잠잠 기간이 지나지 않았다 */
	ACTIVE,

	/** 잠잠 기간이 지났고 끝남 기간은 지나지 않았다 */
	QUIET,

	/** 끝남 기간이 지났다 — 잇기를 막지 않는다. 뒤 기사가 있는 사건이 붙으면 그 시각으로 다시 정한다 */
	ENDED

}
