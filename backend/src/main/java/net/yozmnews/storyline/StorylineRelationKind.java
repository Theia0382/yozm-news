package net.yozmnews.storyline;

/**
 * 스토리라인 사이 관계의 종류 · docs/specs/storyline.md §3 · storyline-R25
 */
public enum StorylineRelationKind {

	/** 포함 — {@code source} 가 {@code target} 을 포함한다 */
	CONTAINS,

	/** 이어짐 — {@code target} 이 {@code source} 에 이어진다 */
	FOLLOWS

}
