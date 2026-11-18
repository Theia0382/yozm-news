package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 구독 제안 판정 — 정본 — docs/specs/digest.md §5 · digest-R41
 * <p>
 * 지금의 관심이 있는 중분류 t 가운데 다음을 모두 지나는 것을 제안한다.
 * <ul>
 * <li>관심 x ≥ {@link InterestRules#SUGGESTION_THRESHOLD}</li>
 * <li>구독 행이 덮는 중분류가 아니다</li>
 * <li>거절이 없거나, 거절 시각 + {@link InterestRules#SUGGESTION_DECLINE_PERIOD} 가 지났고 지금 x 가 거절 때 x 보다 크다(같으면 내지 않는다
 * — 거절 때 x 가 상한 1 이면 다시 내지 않는다)</li>
 * </ul>
 * 순서는 관심 내림차순, 같으면 태그 체계 순이다.
 */
public final class TopicSuggestionRule {

	private TopicSuggestionRule() {
	}

	/**
	 * @param levels 지금의 관심 — 활성 중분류
	 * @param subscribed 구독 행이 덮는 활성 중분류 전체
	 * @param declines 활성 중분류 → 거절
	 * @param order 활성 체계의 주제 순서 — 관심이 같을 때의 순서
	 * @param now 지금
	 * @return 제안할 중분류 — 관심 내림차순, 같으면 체계 순
	 * @throws IllegalArgumentException 제안할 중분류가 순서 표에 없을 때
	 */
	public static List<String> suggest(InterestLevels levels, Set<String> subscribed,
			Map<String, DeclinedTopic> declines, Map<String, Integer> order, Instant now) {
		Objects.requireNonNull(levels, "levels");
		Objects.requireNonNull(subscribed, "subscribed");
		Objects.requireNonNull(declines, "declines");
		Objects.requireNonNull(order, "order");
		Objects.requireNonNull(now, "now");
		List<String> chosen = levels.levels()
			.entrySet()
			.stream()
			.filter(entry -> entry.getValue() >= InterestRules.SUGGESTION_THRESHOLD)
			.map(Map.Entry::getKey)
			.filter(subtopic -> !subscribed.contains(subtopic))
			.filter(subtopic -> !declines.containsKey(subtopic)
					|| !blocks(declines.get(subtopic), levels.level(subtopic), now))
			.toList();
		chosen.forEach(subtopic -> {
			if (!order.containsKey(subtopic)) {
				throw new IllegalArgumentException("제안할 중분류가 체계 순서에 없다: " + subtopic);
			}
		});
		return chosen.stream()
			.sorted(Comparator.comparingDouble((String subtopic) -> levels.level(subtopic))
				.reversed()
				.thenComparing(order::get))
			.toList();
	}

	/** 거절이 지금 제안을 막는가 — 기간이 지나지 않았거나, 지금 관심이 거절 때 관심보다 크지 않다 */
	static boolean blocks(DeclinedTopic decline, double level, Instant now) {
		boolean periodOver = !now.isBefore(decline.declinedAt().plus(InterestRules.SUGGESTION_DECLINE_PERIOD));
		return !periodOver || level <= decline.level();
	}

}
