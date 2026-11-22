package net.yozmnews.user.domain.userstoryline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 씨앗을 묶음으로 — 정본 · docs/specs/user.md §5.
 * <p>
 * 씨앗마다 추적 대상은 그 씨앗과 그 씨앗에서 이어짐을 앞 → 뒤로 따라 닿는 꼭대기다. 추적 대상이 하나라도 겹치는 씨앗들은 한 묶음이다(겹침을
 * 거듭 따른다) — 갈라지는 가지도, 한 흐름으로 모이는 흐름들도 한 묶음이 된다. 한 꼭대기는 한 묶음에만 든다.
 */
public final class UserStorylineGroups {

	private UserStorylineGroups() {
	}

	/**
	 * 묶음 하나.
	 *
	 * @param tops 추적 대상 꼭대기 — 비지 않음
	 * @param seedTops 그 묶음에 든 씨앗 꼭대기 — 비지 않음, {@code tops} 의 부분
	 */
	public record Group(Set<Long> tops, Set<Long> seedTops) {

		public Group {
			tops = Set.copyOf(Objects.requireNonNull(tops, "tops"));
			seedTops = Set.copyOf(Objects.requireNonNull(seedTops, "seedTops"));
			if (seedTops.isEmpty() || !tops.containsAll(seedTops)) {
				throw new IllegalArgumentException("묶음은 씨앗을 하나 이상 갖고 씨앗은 추적 대상에 든다: " + tops + " · " + seedTops);
			}
		}

	}

	/**
	 * @param seedTops 씨앗 꼭대기
	 * @param continuations 씨앗 → 이어짐으로 닿는 꼭대기 — 키가 없는 씨앗은 닿는 것이 없다
	 * @return 묶음 — 가장 작은 꼭대기 ID 순
	 */
	public static List<Group> of(Set<Long> seedTops, Map<Long, Set<Long>> continuations) {
		Objects.requireNonNull(seedTops, "seedTops");
		Objects.requireNonNull(continuations, "continuations");
		Map<Long, Long> parent = new HashMap<>();
		for (long seed : new TreeSet<>(seedTops)) {
			parent.putIfAbsent(seed, seed);
			for (long reached : continuations.getOrDefault(seed, Set.of())) {
				parent.putIfAbsent(reached, reached);
				union(parent, seed, reached);
			}
		}
		Map<Long, Set<Long>> tops = new TreeMap<>();
		Map<Long, Set<Long>> seeds = new TreeMap<>();
		for (long top : parent.keySet()) {
			long root = find(parent, top);
			tops.computeIfAbsent(root, ignored -> new TreeSet<>()).add(top);
			if (seedTops.contains(top)) {
				seeds.computeIfAbsent(root, ignored -> new TreeSet<>()).add(top);
			}
		}
		List<Group> groups = new ArrayList<>();
		tops.forEach((root, members) -> groups.add(new Group(members, seeds.get(root))));
		groups.sort(Comparator.comparingLong(group -> new TreeSet<>(group.tops()).first()));
		return List.copyOf(groups);
	}

	private static long find(Map<Long, Long> parent, long node) {
		long root = node;
		while (parent.get(root) != root) {
			root = parent.get(root);
		}
		return root;
	}

	/** 작은 쪽을 뿌리로 — 결과가 입력 순서에 기대지 않는다 */
	private static void union(Map<Long, Long> parent, long a, long b) {
		long rootA = find(parent, a);
		long rootB = find(parent, b);
		if (rootA != rootB) {
			parent.put(Math.max(rootA, rootB), Math.min(rootA, rootB));
		}
	}

}
