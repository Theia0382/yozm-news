package net.yozmnews.storyline.domain.relation;

import java.util.Objects;

/**
 * 스토리라인 둘 사이의 관계 하나 — docs/specs/storyline.md §4 · storyline-R25.
 *
 * @param sourceId 포함의 상위 · 이어짐의 앞
 * @param targetId 포함의 하위 · 이어짐의 뒤
 */
public record Relation(long sourceId, long targetId, RelationKind kind) {

	/**
	 * @throws IllegalArgumentException 두 ID 가 같을 때 — 스토리라인은 자기 자신과 관계를 갖지 않는다
	 */
	public Relation {
		Objects.requireNonNull(kind, "kind");
		if (sourceId == targetId) {
			throw new IllegalArgumentException("스토리라인은 자기 자신과 관계를 갖지 않는다: " + sourceId);
		}
	}

	/** 이 관계가 그 스토리라인을 끝으로 갖는가 */
	public boolean touches(long storylineId) {
		return this.sourceId == storylineId || this.targetId == storylineId;
	}

	/** 이 관계가 그 쌍의 관계인가 — 방향과 무관 */
	public boolean connects(long a, long b) {
		return (this.sourceId == a && this.targetId == b) || (this.sourceId == b && this.targetId == a);
	}

}
