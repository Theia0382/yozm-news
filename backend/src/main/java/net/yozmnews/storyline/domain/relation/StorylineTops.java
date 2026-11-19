package net.yozmnews.storyline.domain.relation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.storyline.domain.storyline.DirectMembership;
import org.jspecify.annotations.Nullable;

/**
 * 사건마다 그 사건이 걸친 포함 나무의 꼭대기와 그 순서 — 정본 · docs/specs/storyline.md §5 · storyline-R37.
 * <p>
 * 꼭대기는 사건이 직접 든 스토리라인에서 포함의 상위를 끝까지 따른 뿌리다 — 상위가 없으면 자기다. 이어짐은 따르지 않는다. 순서는 그 꼭대기
 * 아래 직접 소속의 가장 높은 소속 점수 내림차순이다 — 점수 없는 소속(합치기로 옮긴 것)만 있는 꼭대기는 뒤, 같으면 꼭대기 ID 오름차순. 점수는
 * 순서를 정하는 데만 쓰고 내지 않는다.
 */
public final class StorylineTops {

	private StorylineTops() {
	}

	/**
	 * @param memberships 사건들의 직접 소속 — 순서 무관
	 * @return 사건 → 꼭대기 ID 목록(순서대로, 비지 않음). 소속이 없는 사건은 빠진다
	 */
	public static Map<Long, List<Long>> of(List<DirectMembership> memberships, RelationGraph graph) {
		Objects.requireNonNull(memberships, "memberships");
		Objects.requireNonNull(graph, "graph");
		Map<Long, Map<Long, @Nullable Double>> best = new LinkedHashMap<>();
		Map<Long, Long> roots = new HashMap<>();
		for (DirectMembership membership : memberships) {
			long root = roots.computeIfAbsent(membership.storylineId(), id -> rootOf(id, graph));
			Map<Long, @Nullable Double> ofEvent = best.computeIfAbsent(membership.eventId(), id -> new HashMap<>());
			if (!ofEvent.containsKey(root)) {
				ofEvent.put(root, membership.score());
			}
			else {
				ofEvent.put(root, higher(ofEvent.get(root), membership.score()));
			}
		}
		Map<Long, List<Long>> tops = new LinkedHashMap<>();
		best.forEach((event, scores) -> {
			List<Map.Entry<Long, @Nullable Double>> entries = new ArrayList<>(scores.entrySet());
			entries.sort(ORDER);
			tops.put(event, entries.stream().map(Map.Entry::getKey).toList());
		});
		return tops;
	}

	/** 점수 있는 꼭대기 먼저(높은 순), 점수 없는 꼭대기 뒤, 같으면 꼭대기 ID 오름차순 */
	private static final Comparator<Map.Entry<Long, @Nullable Double>> ORDER = Comparator
		.comparing((Map.Entry<Long, @Nullable Double> entry) -> entry.getValue() == null)
		.thenComparing(entry -> Objects.requireNonNullElse(entry.getValue(), 0.0), Comparator.reverseOrder())
		.thenComparingLong(Map.Entry::getKey);

	private static long rootOf(long storylineId, RelationGraph graph) {
		List<Long> ancestors = graph.ancestors(storylineId);
		return ancestors.isEmpty() ? storylineId : ancestors.getLast();
	}

	private static @Nullable Double higher(@Nullable Double a, @Nullable Double b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return Math.max(a, b);
	}

}
