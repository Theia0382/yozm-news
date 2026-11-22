package net.yozmnews.storyline.domain.relation;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 스토리라인마다 그 포함 나무의 꼭대기에서 이어짐을 앞 → 뒤로 끝까지 따라 닿는 꼭대기 — 정본 · docs/specs/storyline.md §5.
 * <p>
 * 이어짐은 아무 스토리라인 둘 사이에 맺히므로 두 끝을 포함 나무의 꼭대기({@link StorylineTops#rootOf})로 올려 꼭대기 사이의 고리로 본다. 두
 * 끝의 꼭대기가 같은 고리(한 나무 안의 이어짐)는 버린다. 순환 금지(storyline-R25)는 같은 종류의 원래 스토리라인 사이에서만 확인하므로 올린
 * 고리는 순환을 이룰 수 있다 — 이미 본 꼭대기는 다시 가지 않는다. 뒤 → 앞으로는 따르지 않는다.
 */
public final class TopContinuations {

	private TopContinuations() {
	}

	/**
	 * @param storylineIds 합쳐지지 않은 스토리라인 — 꼭대기가 아니어도 된다
	 * @return 받은 스토리라인 → 닿는 꼭대기(자기 꼭대기는 없다). 닿는 것이 없으면 빈 집합
	 */
	public static Map<Long, Set<Long>> of(Collection<Long> storylineIds, RelationGraph graph) {
		Objects.requireNonNull(storylineIds, "storylineIds");
		Objects.requireNonNull(graph, "graph");
		Map<Long, Set<Long>> later = laterTops(graph);
		Map<Long, Set<Long>> reached = new LinkedHashMap<>();
		Map<Long, Set<Long>> ofTop = new HashMap<>();
		for (long storylineId : storylineIds) {
			long top = StorylineTops.rootOf(storylineId, graph);
			reached.put(storylineId, ofTop.computeIfAbsent(top, start -> reachable(start, later)));
		}
		return reached;
	}

	/** 꼭대기 → 이어짐으로 바로 뒤에 오는 꼭대기 — 두 끝을 꼭대기로 올리고 자기 고리는 버린다 */
	private static Map<Long, Set<Long>> laterTops(RelationGraph graph) {
		Map<Long, Set<Long>> later = new HashMap<>();
		Map<Long, Long> roots = new HashMap<>();
		for (Relation relation : graph.relations()) {
			if (relation.kind() != RelationKind.FOLLOWS) {
				continue;
			}
			long source = roots.computeIfAbsent(relation.sourceId(), id -> StorylineTops.rootOf(id, graph));
			long target = roots.computeIfAbsent(relation.targetId(), id -> StorylineTops.rootOf(id, graph));
			if (source != target) {
				later.computeIfAbsent(source, ignored -> new TreeSet<>()).add(target);
			}
		}
		return later;
	}

	private static Set<Long> reachable(long start, Map<Long, Set<Long>> later) {
		Set<Long> seen = new TreeSet<>();
		Deque<Long> next = new ArrayDeque<>();
		seen.add(start);
		next.add(start);
		while (!next.isEmpty()) {
			for (long target : later.getOrDefault(next.remove(), Set.of())) {
				if (seen.add(target)) {
					next.add(target);
				}
			}
		}
		seen.remove(start);
		return Set.copyOf(seen);
	}

}
