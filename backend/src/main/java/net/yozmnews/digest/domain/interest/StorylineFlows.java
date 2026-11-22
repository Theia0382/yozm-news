package net.yozmnews.digest.domain.interest;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.storyline.StorylineId;

/**
 * 사용자 스토리라인 묶음 — 스토리라인 관심의 키. 꼭대기마다 그것을 추적 대상으로 가진 사용자 스토리라인(묶음 번호)과 추적 중인 묶음.
 * docs/specs/digest.md §5
 * <p>
 * 묶음은 {@code user} 가 낸다 — 사용자가 관심 신호를 준 흐름과 그 흐름이 이어짐으로 따라가는 뒤 흐름이 한 묶음이다. 묶음 번호는 이 값 안에서만
 * 뜻이 있다. 묶음이 없는 꼭대기는 스토리라인 관심의 키가 없다(중립).
 *
 * @param flowOfTop 꼭대기 → 묶음 번호 — 한 꼭대기는 한 묶음에만 든다
 * @param tracked 흐름 추적(구독) 중인 묶음 번호
 */
public record StorylineFlows(Map<StorylineId, Integer> flowOfTop, Set<Integer> tracked) {

	/** 묶음 없음 */
	public static final StorylineFlows NONE = new StorylineFlows(Map.of(), Set.of());

	/**
	 * @throws IllegalArgumentException 추적 중인 묶음 번호가 어느 꼭대기의 묶음도 아닐 때
	 */
	public StorylineFlows {
		flowOfTop = Map.copyOf(Objects.requireNonNull(flowOfTop, "flowOfTop"));
		tracked = Set.copyOf(Objects.requireNonNull(tracked, "tracked"));
		if (!flowOfTop.values().containsAll(tracked)) {
			throw new IllegalArgumentException("추적 중인 묶음에 꼭대기가 없다: " + tracked);
		}
	}

	/** 그 꼭대기의 묶음 — 없으면 빈 값 */
	public Optional<Integer> flowOf(StorylineId top) {
		Objects.requireNonNull(top, "top");
		return Optional.ofNullable(this.flowOfTop.get(top));
	}

	/** 추적 중인 묶음의 모든 꼭대기 — 추적한 흐름과 그 흐름이 따라가는 뒤 흐름 */
	public Set<StorylineId> trackedTops() {
		return this.flowOfTop.entrySet()
			.stream()
			.filter(entry -> this.tracked.contains(entry.getValue()))
			.map(Map.Entry::getKey)
			.collect(Collectors.toUnmodifiableSet());
	}

}
