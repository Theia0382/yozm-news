package net.yozmnews.digest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;

/**
 * 기준 시각 하나의 관심과 그 앞 기준 시각 대비 변화 — 관심사 히스토리 조회가 낸다. docs/specs/digest.md §3 · digest-R35 · digest-R36
 *
 * @param reference 기준 시각
 * @param levels 주제 중분류(활성 키) → 관심 x(−1 ~ 1) — 노출 · 구독 신호가 있는 중분류만
 * @param entered 새로 생긴 관심 — 앞 기준 시각에 관심 있음이 아니었고 이 기준 시각에 관심 있음인 중분류
 * @param left 사라진 관심 — 앞 기준 시각에 관심 있음이었고 이 기준 시각에 아닌 중분류
 * @param subscriptionChanges 앞 기준 시각 이상 · 이 기준 시각 미만에 사용자가 직접 바꾼 주제 구독 — 시각 순
 * @param storylineLevels 스토리라인 포함 나무의 꼭대기 → 스토리라인 관심 x(−1 ~ 1) — 신호가 있는 꼭대기만
 * @param storylineEntered 새로 보이게 된 꼭대기 — 앞 기준 시각에 보이지 않았고 이 기준 시각에 관심이 만들 기준 이상인 꼭대기
 * @param storylineLeft 보이지 않게 된 꼭대기 — 앞 기준 시각에 보였고 이 기준 시각에 관심이 남길 기준 아래인 꼭대기
 */
public record InterestPoint(Instant reference, Map<String, Double> levels, Set<String> entered, Set<String> left,
		List<TopicSubscriptionStep> subscriptionChanges, Map<StorylineId, Double> storylineLevels,
		Set<StorylineId> storylineEntered, Set<StorylineId> storylineLeft) {

	public InterestPoint {
		Objects.requireNonNull(reference, "reference");
		levels = Map.copyOf(Objects.requireNonNull(levels, "levels"));
		entered = Set.copyOf(Objects.requireNonNull(entered, "entered"));
		left = Set.copyOf(Objects.requireNonNull(left, "left"));
		subscriptionChanges = List.copyOf(Objects.requireNonNull(subscriptionChanges, "subscriptionChanges"));
		storylineLevels = Map.copyOf(Objects.requireNonNull(storylineLevels, "storylineLevels"));
		storylineEntered = Set.copyOf(Objects.requireNonNull(storylineEntered, "storylineEntered"));
		storylineLeft = Set.copyOf(Objects.requireNonNull(storylineLeft, "storylineLeft"));
	}

}
