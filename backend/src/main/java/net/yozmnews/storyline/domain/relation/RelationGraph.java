package net.yozmnews.storyline.domain.relation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 지금 관계들의 구조 — 구조 제약의 정본 · docs/specs/storyline.md §4 · storyline-R25.
 * <p>
 * 포함은 상위가 하나인 나무이고, 포함끼리 · 이어짐끼리 순환을 이루지 않는다. 이 값은 넘긴 관계들이 그 제약을 지킨다고 믿지 않는다 — 훑을 때
 * 이미 본 스토리라인은 다시 가지 않는다.
 */
public final class RelationGraph {

	private final List<Relation> relations;

	public RelationGraph(Collection<Relation> relations) {
		this.relations = List.copyOf(relations);
	}

	/** 이 그래프의 관계들 */
	public List<Relation> relations() {
		return this.relations;
	}

	/** 그 쌍의 관계를 뺀 그래프 — 방향과 무관 */
	public RelationGraph without(long a, long b) {
		return new RelationGraph(this.relations.stream().filter(relation -> !relation.connects(a, b)).toList());
	}

	/** 그 쌍의 관계 — 방향과 무관 */
	public Optional<Relation> between(long a, long b) {
		return this.relations.stream().filter(relation -> relation.connects(a, b)).findFirst();
	}

	/** 포함의 상위 — 없으면 빈 값 */
	public Optional<Long> parentOf(long storylineId) {
		return this.relations.stream()
			.filter(relation -> relation.kind() == RelationKind.CONTAINS && relation.targetId() == storylineId)
			.map(Relation::sourceId)
			.findFirst();
	}

	/** 포함을 따라 내려간 모든 하위 — 자기 자신은 없다 */
	public Set<Long> descendants(long storylineId) {
		Set<Long> found = new TreeSet<>(reachable(storylineId, RelationKind.CONTAINS));
		found.remove(storylineId);
		return found;
	}

	/** 포함을 따라 올라간 상위들 — 가까운 순. 자기 자신은 없다 */
	public List<Long> ancestors(long storylineId) {
		List<Long> ancestors = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		seen.add(storylineId);
		Optional<Long> parent = parentOf(storylineId);
		while (parent.isPresent() && seen.add(parent.get())) {
			ancestors.add(parent.get());
			parent = parentOf(parent.get());
		}
		return ancestors;
	}

	/** 넣으면 순환이 되는가 — 넣을 관계의 뒤에서 같은 종류의 고리를 따라 앞에 닿는가 */
	public boolean closesCycle(Relation proposed) {
		return reachable(proposed.targetId(), proposed.kind()).contains(proposed.sourceId());
	}

	/** 그 종류의 고리를 앞 → 뒤로 따라 닿는 스토리라인 — 시작한 것 포함 */
	private Set<Long> reachable(long from, RelationKind kind) {
		Set<Long> seen = new HashSet<>();
		Deque<Long> next = new ArrayDeque<>();
		seen.add(from);
		next.add(from);
		while (!next.isEmpty()) {
			long current = next.remove();
			for (Relation relation : this.relations) {
				if (relation.kind() == kind && relation.sourceId() == current && seen.add(relation.targetId())) {
					next.add(relation.targetId());
				}
			}
		}
		return seen;
	}

}
