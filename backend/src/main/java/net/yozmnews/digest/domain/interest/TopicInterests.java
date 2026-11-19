package net.yozmnews.digest.domain.interest;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 한 사용자의 주제 중분류마다 관심도 가중치 — {@link InterestFormula} 가 낸다. 맵에 없는 중분류는 중립({@link InterestRules#NEUTRAL})이다.
 * docs/specs/digest.md §5
 */
public final class TopicInterests {

	/** 모든 주제가 중립 */
	public static final TopicInterests NEUTRAL_ALL = new TopicInterests(Map.of());

	private final Map<String, Double> weights;

	/** @param weights 중분류(활성 키) → 가중치 */
	public TopicInterests(Map<String, Double> weights) {
		this.weights = Map.copyOf(Objects.requireNonNull(weights, "weights"));
	}

	/** 중분류 하나의 가중치 — 없으면 중립 */
	public double weight(String subtopic) {
		Objects.requireNonNull(subtopic, "subtopic");
		return this.weights.getOrDefault(subtopic, InterestRules.NEUTRAL);
	}

	/** 후보 사건의 가중치 — 사건의 주제 중분류들의 가중치 산술 평균(docs/specs/digest.md digest-R18). 주제가 없으면 중립 */
	public double weightOf(Set<String> subtopics) {
		Objects.requireNonNull(subtopics, "subtopics");
		if (subtopics.isEmpty()) {
			return InterestRules.NEUTRAL;
		}
		return subtopics.stream().mapToDouble(this::weight).sum() / subtopics.size();
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof TopicInterests interests) && this.weights.equals(interests.weights);
	}

	@Override
	public int hashCode() {
		return this.weights.hashCode();
	}

	@Override
	public String toString() {
		return "TopicInterests" + this.weights;
	}

}
