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
 * 식은 하나이고 키 종류(주제 중분류 등)마다 다른 상수 · 성질은 {@link InterestConstants} 묶음으로 받는다. 기준 시각 T 에서 무게 w(at) =
 * 0.5 ^ (max(0, T − at) ÷ 반감기) — T 뒤의 시각은 무게 1. 키 k 마다
 * <ul>
 * <li>노출 E = k 를 가진 노출 사건의 w(노출 시각) × 몫 × 몫 배수 합 — 몫은 항목 무게 1 을 그 항목의 사건 n 개에 나눈 1/n, 몫 배수는 사건이 그
 * 키에 드는 크기({@link ExposedEvent#keys()} — 주제 중분류는 1). 묶음이 {@link InterestConstants#reactionsReplaceEarlierSilence} 를
 * 켰으면 k 에 반응(좋아요 · 싫어요)한 노출 사건 가운데 가장 늦은 노출 시각보다 엄격히 앞선 무반응 사건은 k 의 노출에서 뺀다 — 키마다 따로
 * 정하므로 같은 사건이 다른 키에는 그대로 든다. 구독 신호는 이 기준을 만들지 않는다</li>
 * <li>반응 F = 같은 사건의 w(노출 시각) × 몫 × 몫 배수 × (좋아요 +1 · 싫어요 −1 · 없음 0) 합 — 반응도 노출 시각 · 몫으로 무게를 매겨 F 가 E 를
 * 넘지 않는다</li>
 * <li>구독 S = k 를 몫으로 가진 신호의 w(바꾼 시각) × 부호 × 몫 합</li>
 * <li>관심 x = clamp(F ÷ (E + K) + γ·S, −1, 1), 가중치 g = 1 + β·x — γ · K · β 는 묶음의 값. 구독 신호는 노출 분모 밖의 항이라
 * 노출이 쌓여도 묽어지지 않는다 — 구독만 한 키의 관심은 γ·S 다</li>
 * </ul>
 * 노출도 구독 신호도 없는 키는 결과에 없다 — 중립(관심 0 · 가중치 1)이다. 주제 중분류의 가중치 변환은 {@link InterestLevels#weights} 가 한다.
 * 조회할 때 계산하고 저장하지 않는다.
 */
public final class InterestFormula {

	private InterestFormula() {
	}

	/**
	 * 관심 x — 노출 또는 구독 신호가 있는 키마다
	 * @param exposed 노출 사건 — 사건마다 하나
	 * @param signals 구독 변경 신호
	 * @param reference 기준 시각 — 다이제스트는 기간 끝, 관심사 히스토리는 기간의 기준 시각, 구독 제안은 부르는 시각
	 * @param constants 이 키 종류의 상수
	 * @return 키 → 관심 x(−1 ~ 1)
	 */
	public static <K> Map<K, Double> levels(Collection<ExposedEvent<K>> exposed,
			Collection<SubscriptionSignal<K>> signals, Instant reference, InterestConstants constants) {
		Objects.requireNonNull(exposed, "exposed");
		Objects.requireNonNull(signals, "signals");
		Objects.requireNonNull(reference, "reference");
		Objects.requireNonNull(constants, "constants");
		Map<K, Instant> lastReacted = constants.reactionsReplaceEarlierSilence() ? lastReacted(exposed) : Map.of();
		Map<K, Double> exposures = new HashMap<>();
		Map<K, Double> reactions = new HashMap<>();
		Map<K, Double> subscriptions = new HashMap<>();
		for (ExposedEvent<K> event : exposed) {
			double weight = decay(event.exposedAt(), reference, constants.halfLife()) * event.share();
			double reaction = weight * value(event.reaction());
			event.keys().forEach((key, multiple) -> {
				if (silenceBeforeReaction(event, lastReacted.get(key))) {
					return;
				}
				exposures.merge(key, weight * multiple, Double::sum);
				reactions.merge(key, reaction * multiple, Double::sum);
			});
		}
		for (SubscriptionSignal<K> signal : signals) {
			double weight = decay(signal.occurredAt(), reference, constants.halfLife()) * signal.sign();
			signal.shares().forEach((key, share) -> subscriptions.merge(key, weight * share, Double::sum));
		}
		Set<K> keys = new HashSet<>(exposures.keySet());
		keys.addAll(subscriptions.keySet());
		Map<K, Double> levels = new HashMap<>();
		for (K key : keys) {
			double reacted = reactions.getOrDefault(key, 0.0)
					/ (exposures.getOrDefault(key, 0.0) + constants.priorExposures());
			double subscribed = constants.subscriptionSignal() * subscriptions.getOrDefault(key, 0.0);
			levels.put(key, Math.max(-1.0, Math.min(1.0, reacted + subscribed)));
		}
		return Map.copyOf(levels);
	}

	/** 키마다 반응(좋아요 · 싫어요)한 노출 사건 가운데 가장 늦은 노출 시각 */
	private static <K> Map<K, Instant> lastReacted(Collection<ExposedEvent<K>> exposed) {
		Map<K, Instant> last = new HashMap<>();
		for (ExposedEvent<K> event : exposed) {
			if (event.reaction() != FeedbackValue.NONE) {
				event.keys().keySet().forEach(key -> last.merge(key, event.exposedAt(), (a, b) -> a.isAfter(b) ? a : b));
			}
		}
		return last;
	}

	/** 그 키의 가장 늦은 반응 노출보다 엄격히 앞선 무반응 사건인가 — 그 키의 노출에서 뺀다 */
	private static <K> boolean silenceBeforeReaction(ExposedEvent<K> event, Instant lastReacted) {
		return lastReacted != null && event.reaction() == FeedbackValue.NONE && event.exposedAt().isBefore(lastReacted);
	}

	/** 무게 — 0.5 ^ (max(0, reference − at) ÷ 반감기) */
	public static double decay(Instant at, Instant reference, Duration halfLife) {
		Objects.requireNonNull(at, "at");
		Objects.requireNonNull(reference, "reference");
		Objects.requireNonNull(halfLife, "halfLife");
		Duration age = Duration.between(at, reference);
		if (age.isNegative()) {
			return 1.0;
		}
		return Math.pow(0.5, (double) age.toMillis() / halfLife.toMillis());
	}

	private static double value(FeedbackValue reaction) {
		return switch (reaction) {
			case LIKE -> 1.0;
			case DISLIKE -> -1.0;
			case NONE -> 0.0;
		};
	}

}
