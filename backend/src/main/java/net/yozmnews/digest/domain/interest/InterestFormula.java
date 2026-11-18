package net.yozmnews.digest.domain.interest;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.feedback.FeedbackValue;

/**
 * 관심도 산식 — 정본 — docs/specs/digest.md §5
 * <p>
 * 기준 시각 T 에서 무게 w(at) = 0.5 ^ (max(0, T − at) ÷ {@link InterestRules#HALF_LIFE}) — T 뒤의 시각은 무게 1. 주제 중분류 t 마다
 * <ul>
 * <li>노출 E = t 가 붙은 노출 기사의 w(노출 시각) 합</li>
 * <li>반응 F = 같은 기사의 w(노출 시각) × (좋아요 +1 · 싫어요 −1 · 없음 0) 합 — 반응도 노출 시각으로 감쇠해 F 가 E 를 넘지 않는다</li>
 * <li>구독 S = t 를 몫으로 가진 신호의 w(바꾼 시각) × 부호 × 몫 합</li>
 * <li>관심 x = clamp((F + γ·S) ÷ (E + K), −1, 1), 가중치 g = 1 + β·x — γ {@link InterestRules#SUBSCRIPTION_SIGNAL}, K
 * {@link InterestRules#PRIOR_EXPOSURES}, β {@link InterestRules#STRENGTH}</li>
 * </ul>
 * 노출도 구독 신호도 없는 주제는 중립(관심 0 · 가중치 1)이다. 가중치 변환은 {@link InterestLevels#weights} 가 한다. 조회할 때 계산하고
 * 저장하지 않는다.
 */
public final class InterestFormula {

	private InterestFormula() {
	}

	/**
	 * 가중치 — {@link #levels} 의 관심을 {@link InterestLevels#weights} 로 옮긴 것
	 * @param exposed 노출 기사 — 기사마다 하나
	 * @param signals 구독 변경 신호
	 * @param reference 기준 시각 — 다이제스트는 기간 끝, 관심사 히스토리는 기간의 기준 시각
	 */
	public static TopicInterests interests(Collection<ExposedArticle> exposed, Collection<SubscriptionSignal> signals,
			Instant reference) {
		return levels(exposed, signals, reference).weights();
	}

	/**
	 * 관심 x — 노출 또는 구독 신호가 있는 중분류마다
	 * @param exposed 노출 기사 — 기사마다 하나
	 * @param signals 구독 변경 신호
	 * @param reference 기준 시각 — 다이제스트는 기간 끝, 관심사 히스토리는 기간의 기준 시각
	 */
	public static InterestLevels levels(Collection<ExposedArticle> exposed, Collection<SubscriptionSignal> signals,
			Instant reference) {
		Objects.requireNonNull(exposed, "exposed");
		Objects.requireNonNull(signals, "signals");
		Objects.requireNonNull(reference, "reference");
		Map<String, Double> exposures = new HashMap<>();
		Map<String, Double> reactions = new HashMap<>();
		Map<String, Double> subscriptions = new HashMap<>();
		for (ExposedArticle article : exposed) {
			double weight = decay(article.exposedAt(), reference);
			double reaction = weight * value(article.reaction());
			for (String subtopic : article.subtopics()) {
				exposures.merge(subtopic, weight, Double::sum);
				reactions.merge(subtopic, reaction, Double::sum);
			}
		}
		for (SubscriptionSignal signal : signals) {
			double weight = decay(signal.occurredAt(), reference) * signal.sign();
			signal.shares().forEach((subtopic, share) -> subscriptions.merge(subtopic, weight * share, Double::sum));
		}
		Set<String> topics = new HashSet<>(exposures.keySet());
		topics.addAll(subscriptions.keySet());
		Map<String, Double> levels = new HashMap<>();
		for (String subtopic : topics) {
			double numerator = reactions.getOrDefault(subtopic, 0.0)
					+ InterestRules.SUBSCRIPTION_SIGNAL * subscriptions.getOrDefault(subtopic, 0.0);
			double denominator = exposures.getOrDefault(subtopic, 0.0) + InterestRules.PRIOR_EXPOSURES;
			levels.put(subtopic, Math.max(-1.0, Math.min(1.0, numerator / denominator)));
		}
		return new InterestLevels(levels);
	}

	/** 무게 — 0.5 ^ (max(0, reference − at) ÷ 반감기) */
	public static double decay(Instant at, Instant reference) {
		Objects.requireNonNull(at, "at");
		Objects.requireNonNull(reference, "reference");
		Duration age = Duration.between(at, reference);
		if (age.isNegative()) {
			return 1.0;
		}
		return Math.pow(0.5, (double) age.toMillis() / InterestRules.HALF_LIFE.toMillis());
	}

	private static double value(FeedbackValue reaction) {
		return switch (reaction) {
			case LIKE -> 1.0;
			case DISLIKE -> -1.0;
			case NONE -> 0.0;
		};
	}

}
