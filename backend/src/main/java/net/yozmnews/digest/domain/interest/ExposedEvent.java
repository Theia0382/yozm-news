package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.feedback.FeedbackValue;

/**
 * 관심도가 보는 노출 사건 하나 — 키 종류(주제 중분류 등)에 무관하다. docs/specs/digest.md §5
 *
 * @param keys 사건이 관심을 더하는 키 → 그 키에 드는 몫 배수(0 초과 1 이하). 주제 중분류는 사건의 주제마다 1 이다
 * @param exposedAt 그 사용자의 다이제스트가 이 사건을 보인 가장 늦은 시각 — 노출과 그 반응의 감쇠를 함께 정한다
 * @param share 그 가장 늦은 노출의 몫 — 노출과 그 반응에 함께 곱한다. 0 보다 크고 1 이하
 * @param reaction 그 사건에 남은 마지막 값 — 없으면 {@link FeedbackValue#NONE}
 * @param <K> 관심의 키
 */
public record ExposedEvent<K>(Map<K, Double> keys, Instant exposedAt, double share, FeedbackValue reaction) {

	/**
	 * @throws IllegalArgumentException 몫 배수가 0 초과 1 이하가 아니거나 몫이 범위 밖일 때
	 */
	public ExposedEvent {
		keys = Map.copyOf(Objects.requireNonNull(keys, "keys"));
		keys.forEach((key, multiple) -> {
			if (!(multiple > 0.0 && multiple <= 1.0)) {
				throw new IllegalArgumentException("몫 배수는 0 초과 1 이하여야 한다: " + key + "=" + multiple);
			}
		});
		Objects.requireNonNull(exposedAt, "exposedAt");
		Objects.requireNonNull(reaction, "reaction");
		Exposure.requireShare(share);
	}

}
