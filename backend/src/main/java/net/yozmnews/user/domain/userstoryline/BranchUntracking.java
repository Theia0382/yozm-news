package net.yozmnews.user.domain.userstoryline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 추적 중인 사용자 스토리라인 일부를 끌 때의 추적 씨앗 — 정본 · docs/specs/user.md §5 · user-R52.
 * <p>
 * 끄는 사용자 스토리라인과 남는 추적 중 사용자 스토리라인이 꼭대기를 함께 가진 추적 씨앗(갈라지기 전 꼭대기를 눌러 갈래 모두가 가진 씨앗)은 양쪽 모두
 * 각자의 헤드로 바꾼다. 그대로 두면 남는 쪽의 그 씨앗이 다음 맞추기에서 그 꼭대기를 가진 묶음 모두로 가(user-R66 ④) 끈 쪽이 다시 추적 중이 된다.
 * 바꾼 뒤 끄는 쪽의 씨앗이 사용자의 해제가 된다 — 관심 신호(뺌)가 끈 쪽에만 든다.
 *
 * @param seeds 남는 추적 중 사용자 스토리라인 가운데 씨앗이 바뀐 것 → 바뀐 추적 씨앗(스토리라인 ID)
 * @param splitAdded 씨앗을 바꾸며 새로 생긴 추적 씨앗 — 사용자 신호가 아닌 이력(더함)
 * @param splitRemoved 씨앗을 바꾸며 없어진 추적 씨앗 — 사용자 신호가 아닌 이력(뺌)
 * @param userRemoved 사용자의 해제로 없어지는 추적 씨앗 — 바꾼 뒤 끄는 쪽의 씨앗 가운데 남는 쪽에 없는 것
 */
public record BranchUntracking(Map<Long, Set<Long>> seeds, Set<Long> splitAdded, Set<Long> splitRemoved,
		Set<Long> userRemoved) {

	public BranchUntracking {
		seeds = Map.copyOf(seeds);
		splitAdded = Set.copyOf(splitAdded);
		splitRemoved = Set.copyOf(splitRemoved);
		userRemoved = Set.copyOf(userRemoved);
	}

	/**
	 * @param tracked 추적 중인 사용자 스토리라인 모두 — 맞춘 뒤라 헤드가 있다
	 * @param untracked 끌 사용자 스토리라인 ID — {@code tracked} 의 부분
	 * @throws IllegalArgumentException 헤드가 없는 추적 중 사용자 스토리라인이 있을 때
	 */
	public static BranchUntracking decide(List<StoredUserStoryline> tracked, Set<Long> untracked) {
		Objects.requireNonNull(tracked, "tracked");
		Objects.requireNonNull(untracked, "untracked");
		Set<Long> untrackedTops = new TreeSet<>();
		Set<Long> keptTops = new TreeSet<>();
		for (StoredUserStoryline one : tracked) {
			(untracked.contains(one.id()) ? untrackedTops : keptTops).addAll(one.tops());
		}
		Set<Long> before = new TreeSet<>();
		Set<Long> after = new TreeSet<>();
		Set<Long> untrackedAfter = new TreeSet<>();
		Set<Long> keptAfter = new TreeSet<>();
		Map<Long, Set<Long>> changed = new HashMap<>();
		for (StoredUserStoryline one : tracked) {
			boolean leaving = untracked.contains(one.id());
			Set<Long> opposite = leaving ? keptTops : untrackedTops;
			Set<Long> seeds = new TreeSet<>();
			boolean moved = false;
			for (Map.Entry<Long, Long> seed : one.trackedSeeds().entrySet()) {
				before.add(seed.getKey());
				if (opposite.contains(seed.getValue())) {
					seeds.add(headOf(one));
					moved = true;
				}
				else {
					seeds.add(seed.getKey());
				}
			}
			after.addAll(seeds);
			(leaving ? untrackedAfter : keptAfter).addAll(seeds);
			if (moved && !leaving) {
				changed.put(one.id(), seeds);
			}
		}
		Set<Long> splitAdded = new TreeSet<>(after);
		splitAdded.removeAll(before);
		Set<Long> splitRemoved = new TreeSet<>(before);
		splitRemoved.removeAll(after);
		Set<Long> userRemoved = new TreeSet<>(untrackedAfter);
		userRemoved.removeAll(keptAfter);
		return new BranchUntracking(changed, splitAdded, splitRemoved, userRemoved);
	}

	private static long headOf(StoredUserStoryline one) {
		if (one.head() == null) {
			throw new IllegalArgumentException("헤드가 없는 추적 중 사용자 스토리라인: " + one.id());
		}
		return one.head();
	}

}
