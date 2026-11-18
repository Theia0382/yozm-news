package net.yozmnews.digest.domain.interest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 한 사용자의 주제 중분류마다 관심 x(−1 ~ 1) — {@link InterestFormula#levels} 가 낸다. 맵에 없는 중분류는 0(신호 없음)이다.
 * 가중치({@link TopicInterests})로 옮기는 변환과 "관심 있음"의 판정이 여기 한 곳에 있다. docs/specs/digest.md §5 · digest-R23 · digest-R36
 */
public final class InterestLevels {

	/** 모든 주제에 신호가 없다 */
	public static final InterestLevels NONE = new InterestLevels(Map.of());

	private final Map<String, Double> levels;

	/**
	 * @param levels 중분류(활성 키) → 관심 x
	 * @throws IllegalArgumentException 값이 −1 ~ 1 밖이거나 수가 아닐 때
	 */
	public InterestLevels(Map<String, Double> levels) {
		Objects.requireNonNull(levels, "levels");
		levels.forEach((subtopic, level) -> {
			if (!(level >= -1.0 && level <= 1.0)) {
				throw new IllegalArgumentException("관심은 −1 이상 1 이하여야 한다: " + subtopic + "=" + level);
			}
		});
		this.levels = Map.copyOf(levels);
	}

	/** 중분류 하나의 관심 — 없으면 0 */
	public double level(String subtopic) {
		Objects.requireNonNull(subtopic, "subtopic");
		return this.levels.getOrDefault(subtopic, 0.0);
	}

	/** 신호가 있는 중분류마다 관심 */
	public Map<String, Double> levels() {
		return this.levels;
	}

	/** 가중치 — 중분류마다 g = {@link InterestRules#NEUTRAL} + {@link InterestRules#STRENGTH} × x. 변환의 정본 */
	public TopicInterests weights() {
		Map<String, Double> weights = new HashMap<>();
		this.levels.forEach(
				(subtopic, level) -> weights.put(subtopic, InterestRules.NEUTRAL + InterestRules.STRENGTH * level));
		return new TopicInterests(weights);
	}

	/** 관심 있음 — x 가 {@link InterestRules#PRESENCE_THRESHOLD} 이상인 중분류 */
	public Set<String> present() {
		return this.levels.entrySet()
			.stream()
			.filter(entry -> entry.getValue() >= InterestRules.PRESENCE_THRESHOLD)
			.map(Map.Entry::getKey)
			.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof InterestLevels levels) && this.levels.equals(levels.levels);
	}

	@Override
	public int hashCode() {
		return this.levels.hashCode();
	}

	@Override
	public String toString() {
		return "InterestLevels" + this.levels;
	}

}
