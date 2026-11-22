package net.yozmnews.digest.domain.interest;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.storyline.StorylineId;

/**
 * 한 사용자의 스토리라인(포함 나무의 꼭대기)마다 관심도 가중치 — {@link StorylineLevels#weights} 가 낸다. 맵에 없는 꼭대기는
 * 중립({@link InterestRules#NEUTRAL})이다. docs/specs/digest.md §5
 */
public final class StorylineInterests {

	/** 모든 꼭대기가 중립 */
	public static final StorylineInterests NEUTRAL_ALL = new StorylineInterests(Map.of());

	private final Map<StorylineId, Double> weights;

	/** @param weights 꼭대기 → 가중치 */
	public StorylineInterests(Map<StorylineId, Double> weights) {
		this.weights = Map.copyOf(Objects.requireNonNull(weights, "weights"));
	}

	/** 꼭대기 하나의 가중치 — 없으면 중립 */
	public double weight(StorylineId top) {
		Objects.requireNonNull(top, "top");
		return this.weights.getOrDefault(top, InterestRules.NEUTRAL);
	}

	/** 사건의 가중치 — 그 사건 꼭대기들의 가중치 산술 평균. 꼭대기가 없으면 중립 */
	public double weightOf(List<StorylineId> tops) {
		Objects.requireNonNull(tops, "tops");
		if (tops.isEmpty()) {
			return InterestRules.NEUTRAL;
		}
		return tops.stream().mapToDouble(this::weight).sum() / tops.size();
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof StorylineInterests interests) && this.weights.equals(interests.weights);
	}

	@Override
	public int hashCode() {
		return this.weights.hashCode();
	}

	@Override
	public String toString() {
		return "StorylineInterests" + this.weights;
	}

}
