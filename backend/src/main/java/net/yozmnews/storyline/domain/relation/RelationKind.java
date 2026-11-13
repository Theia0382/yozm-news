package net.yozmnews.storyline.domain.relation;

/**
 * 저장하는 관계의 종류 — docs/specs/storyline.md §4 · storyline-R25.
 * <p>
 * 공개 값 {@code StorylineRelationKind} 와 하나씩 짝이다.
 */
public enum RelationKind {

	/** 포함 — 앞이 상위, 뒤가 하위 */
	CONTAINS,

	/** 이어짐 — 앞이 뒤를 낳았다 */
	FOLLOWS

}
