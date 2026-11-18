package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 구독 변경 신호 하나 — 주제 구독 · 스토리라인 구독의 더함 · 뺌. docs/specs/digest.md §5
 *
 * @param shares 주제 중분류(활성 키) → 몫(0 초과 1 이하). 주제 구독은 덮는 중분류마다 1, 스토리라인 구독은 그 스토리라인의 주제 몫
 * @param sign 더함 +1 · 뺌 −1
 * @param occurredAt 바꾼 시각
 */
public record SubscriptionSignal(Map<String, Double> shares, int sign, Instant occurredAt) {

	public SubscriptionSignal {
		shares = Map.copyOf(Objects.requireNonNull(shares, "shares"));
		shares.forEach((subtopic, share) -> {
			if (!(share > 0.0 && share <= 1.0)) {
				throw new IllegalArgumentException("몫은 0 초과 1 이하여야 한다: " + subtopic + "=" + share);
			}
		});
		if (sign != 1 && sign != -1) {
			throw new IllegalArgumentException("부호는 +1 또는 −1 이어야 한다: " + sign);
		}
		Objects.requireNonNull(occurredAt, "occurredAt");
	}

}
