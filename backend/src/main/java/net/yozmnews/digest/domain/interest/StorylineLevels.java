package net.yozmnews.digest.domain.interest;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.storyline.StorylineId;

/**
 * 한 사용자의 스토리라인(포함 나무의 꼭대기)마다 관심 x(−1 ~ 1) — {@link InterestWindow#levels} 가 {@link InterestRules#STORYLINE} 묶음으로
 * 낸다. 관심의 키는 사용자 스토리라인 묶음({@link UserStorylines})이고, 한 묶음의 꼭대기는 모두 그 묶음의 값을 받는다. 묶음 여럿에 든 꼭대기(갈라지기
 * 전 꼭대기)는 그 묶음들의 값을 모두 받는다 — 관심 · 가중치는 평균, 거름은 모두 거름 기준 이하일 때, 관심 스토리라인 · 남길 스토리라인은 하나라도 기준 이상일
 * 때다. 맵에 없는 꼭대기는 0(신호 없음)이다. 가중치 변환과 거른 꼭대기 · 관심 스토리라인 · 남길 스토리라인의 판정이 여기 한 곳에 있다.
 * docs/specs/digest.md §5
 */
public final class StorylineLevels {

	/** 모든 꼭대기에 신호가 없다 */
	public static final StorylineLevels NONE = new StorylineLevels(Map.of());

	/** 꼭대기 → 그 꼭대기 묶음 값들의 평균 */
	private final Map<StorylineId, Double> levels;

	/** 꼭대기 → 그 꼭대기 묶음 값들의 최대 — 묶음 하나면 평균과 같다 */
	private final Map<StorylineId, Double> highest;

	/**
	 * 꼭대기마다 값 하나(묶음 하나)
	 * @param levels 꼭대기 → 관심 x
	 * @throws IllegalArgumentException 값이 −1 ~ 1 밖이거나 수가 아닐 때
	 */
	public StorylineLevels(Map<StorylineId, Double> levels) {
		this(checked(levels), checked(levels));
	}

	private StorylineLevels(Map<StorylineId, Double> levels, Map<StorylineId, Double> highest) {
		this.levels = Map.copyOf(levels);
		this.highest = Map.copyOf(highest);
	}

	/**
	 * 꼭대기마다 그 꼭대기를 가진 묶음들의 값 — 평균과 최대를 함께 둔다
	 * @param levelsOfTop 꼭대기 → 묶음마다의 관심 x — 빈 목록인 꼭대기는 신호 없음
	 * @throws IllegalArgumentException 값이 −1 ~ 1 밖이거나 수가 아닐 때
	 */
	public static StorylineLevels ofUserStorylines(Map<StorylineId, ? extends Collection<Double>> levelsOfTop) {
		Objects.requireNonNull(levelsOfTop, "levelsOfTop");
		Map<StorylineId, Double> mean = new HashMap<>();
		Map<StorylineId, Double> max = new HashMap<>();
		levelsOfTop.forEach((top, values) -> {
			if (values.isEmpty()) {
				return;
			}
			values.forEach(level -> check(top, level));
			mean.put(top, values.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
			max.put(top, values.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
		});
		return new StorylineLevels(mean, max);
	}

	private static Map<StorylineId, Double> checked(Map<StorylineId, Double> levels) {
		Objects.requireNonNull(levels, "levels");
		levels.forEach(StorylineLevels::check);
		return levels;
	}

	private static void check(StorylineId top, Double level) {
		if (!(level >= -1.0 && level <= 1.0)) {
			throw new IllegalArgumentException("관심은 −1 이상 1 이하여야 한다: " + top + "=" + level);
		}
	}

	/** 꼭대기 하나의 관심(묶음 여럿이면 평균) — 없으면 0 */
	public double level(StorylineId top) {
		Objects.requireNonNull(top, "top");
		return this.levels.getOrDefault(top, 0.0);
	}

	/** 신호가 있는 꼭대기마다 관심(묶음 여럿이면 평균) */
	public Map<StorylineId, Double> levels() {
		return this.levels;
	}

	/** 가중치 — 꼭대기마다 g = {@link InterestRules#NEUTRAL} + β({@link InterestRules#STORYLINE} 의 세기) × x */
	public StorylineInterests weights() {
		Map<StorylineId, Double> weights = new HashMap<>();
		this.levels.forEach(
				(top, level) -> weights.put(top, InterestRules.NEUTRAL + InterestRules.STORYLINE.strength() * level));
		return new StorylineInterests(weights);
	}

	/** 거른 꼭대기 — 그 꼭대기의 묶음이 모두(최대가) {@link InterestRules#EXCLUSION_THRESHOLD} 이하(경계 포함) */
	public Set<StorylineId> excluded() {
		return this.highest.entrySet()
			.stream()
			.filter(entry -> entry.getValue() <= InterestRules.EXCLUSION_THRESHOLD)
			.map(Map.Entry::getKey)
			.collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * 관심 스토리라인 — 묶음 하나라도(최대가) {@link InterestRules#STORYLINE_FOLLOW_THRESHOLD} 이상인 꼭대기와 {@code subscribed} 에서 거른 꼭대기를 뺀 것. 거름이
	 * 앞선다 — 구독한 스토리라인도 거른 꼭대기면 관심 스토리라인이 아니다
	 * @param subscribed 추적 중인 사용자 스토리라인의 꼭대기({@link UserStorylines#trackedTops})
	 */
	public Set<StorylineId> followed(Set<StorylineId> subscribed) {
		Objects.requireNonNull(subscribed, "subscribed");
		Set<StorylineId> followed = new HashSet<>(subscribed);
		this.highest.forEach((top, level) -> {
			if (level >= InterestRules.STORYLINE_FOLLOW_THRESHOLD) {
				followed.add(top);
			}
		});
		followed.removeAll(excluded());
		return Set.copyOf(followed);
	}

	/** 남길 스토리라인 — 묶음 하나라도(최대가) {@link InterestRules#STORYLINE_KEEP_THRESHOLD} 이상인 꼭대기 */
	public Set<StorylineId> kept() {
		return this.highest.entrySet()
			.stream()
			.filter(entry -> entry.getValue() >= InterestRules.STORYLINE_KEEP_THRESHOLD)
			.map(Map.Entry::getKey)
			.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof StorylineLevels levels) && this.levels.equals(levels.levels)
				&& this.highest.equals(levels.highest);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.levels, this.highest);
	}

	@Override
	public String toString() {
		return "StorylineLevels" + this.levels + " max" + this.highest;
	}

}
