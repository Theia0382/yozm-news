package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 구독 변경 신호 하나 — 주제 구독 · 스토리라인 구독의 더함 · 뺌. docs/specs/digest.md §5
 *
 * @param shares 키 → 몫(0 초과 1 이하). 주제 구독은 덮는 중분류마다 1, 스토리라인 구독은 그 스토리라인의 꼭대기를 가진 묶음마다 1
 * @param sign 더함 +1 · 뺌 −1
 * @param occurredAt 바꾼 시각
 * @param <K> 관심의 키
 */
public record SubscriptionSignal<K>(Map<K, Double> shares, int sign, Instant occurredAt) {

	public SubscriptionSignal {
		shares = Map.copyOf(Objects.requireNonNull(shares, "shares"));
		shares.forEach((key, share) -> {
			if (!(share > 0.0 && share <= 1.0)) {
				throw new IllegalArgumentException("몫은 0 초과 1 이하여야 한다: " + key + "=" + share);
			}
		});
		if (sign != 1 && sign != -1) {
			throw new IllegalArgumentException("부호는 +1 또는 −1 이어야 한다: " + sign);
		}
		Objects.requireNonNull(occurredAt, "occurredAt");
	}

}
