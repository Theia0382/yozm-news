package net.yozmnews.digest.domain.interest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.storyline.StorylineId;

/**
 * 사용자 스토리라인 묶음 — 스토리라인 관심의 키. 꼭대기마다 그것을 추적 대상으로 가진 사용자 스토리라인(묶음 번호)들과 추적 중인 묶음.
 * docs/specs/digest.md §5 · digest-R49
 * <p>
 * 묶음은 {@code user} 가 낸다 — 사용자 스토리라인은 헤드마다 하나라, 갈라지기 전 꼭대기는 그 뒤 갈래의 묶음 여럿에 함께 든다. 묶음 번호는 이 값
 * 안에서만 뜻이 있다. 묶음이 없는 꼭대기는 스토리라인 관심의 키가 없다(중립).
 *
 * @param userStorylinesOfTop 꼭대기 → 묶음 번호들 — 비지 않음
 * @param tracked 추적(구독) 중인 묶음 번호
 */
public record UserStorylines(Map<StorylineId, Set<Integer>> userStorylinesOfTop, Set<Integer> tracked) {

	/** 묶음 없음 */
	public static final UserStorylines NONE = new UserStorylines(Map.of(), Set.of());

	/**
	 * @throws IllegalArgumentException 꼭대기의 묶음 집합이 비었거나, 추적 중인 묶음 번호가 어느 꼭대기의 묶음도 아닐 때
	 */
	public UserStorylines {
		Objects.requireNonNull(userStorylinesOfTop, "userStorylinesOfTop");
		Objects.requireNonNull(tracked, "tracked");
		Map<StorylineId, Set<Integer>> copied = new HashMap<>();
		userStorylinesOfTop.forEach((top, userStorylines) -> {
			if (userStorylines.isEmpty()) {
				throw new IllegalArgumentException("꼭대기의 묶음이 비었다: " + top);
			}
			copied.put(top, Set.copyOf(userStorylines));
		});
		userStorylinesOfTop = Map.copyOf(copied);
		tracked = Set.copyOf(tracked);
		Set<Integer> known = userStorylinesOfTop.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
		if (!known.containsAll(tracked)) {
			throw new IllegalArgumentException("추적 중인 묶음에 꼭대기가 없다: " + tracked);
		}
	}

	/** 그 꼭대기의 묶음들 — 없으면 빈 집합 */
	public Set<Integer> userStorylinesOf(StorylineId top) {
		Objects.requireNonNull(top, "top");
		return this.userStorylinesOfTop.getOrDefault(top, Set.of());
	}

	/** 추적 중인 묶음을 하나라도 가진 꼭대기 — 추적한 스토리라인과 그 스토리라인이 따라가는 뒤 스토리라인 */
	public Set<StorylineId> trackedTops() {
		return this.userStorylinesOfTop.entrySet()
			.stream()
			.filter(entry -> entry.getValue().stream().anyMatch(this.tracked::contains))
			.map(Map.Entry::getKey)
			.collect(Collectors.toUnmodifiableSet());
	}

}
