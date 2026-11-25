package net.yozmnews.user.domain.userstoryline;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

/**
 * 씨앗을 묶음으로 — 정본 · docs/specs/user.md §5 · user-R66.
 * <p>
 * 씨앗마다 닿는 꼭대기는 그 씨앗과 그 씨앗에서 이어짐을 앞 → 뒤로 따라 닿는 꼭대기다. 닿는 꼭대기 가운데 뒤 스토리라인이 없는 것이 헤드다 — 빠져나가는 이어짐
 * 없이 서로 이어지는 꼭대기들(순환)은 함께 끝이고 그 가운데 ID 가 가장 작은 것이 헤드다. 헤드마다 묶음 하나: 그 헤드에 닿는 씨앗과, 그 씨앗에서 그
 * 헤드까지 닿는 길의 꼭대기다. 갈라지기 전 꼭대기는 그 뒤 헤드의 묶음 여럿에 함께 든다. 헤드는 묶음마다 하나이고 겹치지 않는다.
 */
public final class UserStorylineGroups {

	private UserStorylineGroups() {
	}

	/**
	 * 묶음 하나.
	 *
	 * @param head 헤드 꼭대기 — {@code tops} 에 든다
	 * @param tops 추적 대상 꼭대기 — 비지 않음
	 * @param seedTops 그 묶음에 든 씨앗 꼭대기 — 비지 않음, {@code tops} 의 부분
	 */
	public record Group(long head, Set<Long> tops, Set<Long> seedTops) {

		public Group {
			tops = Set.copyOf(Objects.requireNonNull(tops, "tops"));
			seedTops = Set.copyOf(Objects.requireNonNull(seedTops, "seedTops"));
			if (!tops.contains(head) || seedTops.isEmpty() || !tops.containsAll(seedTops)) {
				throw new IllegalArgumentException(
						"묶음은 헤드와 씨앗을 갖고 씨앗은 추적 대상에 든다: " + head + " · " + tops + " · " + seedTops);
			}
		}

	}

	/**
	 * @param seedTops 씨앗 꼭대기
	 * @param continuations 꼭대기 → 이어짐으로 닿는 꼭대기(앞 → 뒤 닫힘) — 씨앗과 씨앗에서 닿는 꼭대기 모두의 것. 키가 없는 꼭대기는 닿는 것이
	 *        없다
	 * @return 묶음 — 헤드 ID 순
	 */
	public static List<Group> of(Set<Long> seedTops, Map<Long, Set<Long>> continuations) {
		Objects.requireNonNull(seedTops, "seedTops");
		Objects.requireNonNull(continuations, "continuations");
		Set<Long> reachable = new TreeSet<>(seedTops);
		for (long seed : seedTops) {
			reachable.addAll(reachOf(seed, continuations));
		}
		Map<Long, Group> byHead = new TreeMap<>();
		for (long candidate : reachable) {
			Long head = headOf(candidate, continuations);
			if (head == null || byHead.containsKey(head)) {
				continue;
			}
			Set<Long> tops = new TreeSet<>();
			for (long top : reachable) {
				if (top == head || reachOf(top, continuations).contains(head)) {
					tops.add(top);
				}
			}
			Set<Long> seeds = new TreeSet<>(tops);
			seeds.retainAll(seedTops);
			byHead.put(head, new Group(head, tops, seeds));
		}
		return List.copyOf(byHead.values());
	}

	/**
	 * 그 꼭대기가 끝이면 그 끝의 헤드 — 뒤 스토리라인이 없으면 자신, 닿는 꼭대기가 모두 자신에게 되돌아오면(빠져나가는 이어짐 없는 순환) 그 순환의 가장
	 * 작은 ID. 끝이 아니면 null
	 */
	static @Nullable Long headOf(long top, Map<Long, Set<Long>> continuations) {
		Set<Long> reach = reachOf(top, continuations);
		long head = top;
		for (long next : reach) {
			if (next != top && !reachOf(next, continuations).contains(top)) {
				return null;
			}
			head = Math.min(head, next);
		}
		return head;
	}

	private static Set<Long> reachOf(long top, Map<Long, Set<Long>> continuations) {
		return continuations.getOrDefault(top, Set.of());
	}

}
