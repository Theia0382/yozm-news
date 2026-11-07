package net.yozmnews.event;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 판정 원본 하나가 남긴 병합 판정 기록의 쌍 — 순서 없는 쌍(작은 ID, 큰 ID) · docs/specs/event.md §3 · event-R22.
 *
 * @param probability 그 쌍의 판정 확률
 * @param survivor 두 사건이 지금 같은 사건이면 그 사건(합쳐진 사건은 남는 사건으로 읽는다), 아니면 null
 */
public record MergePair(EventId low, EventId high, double probability, @Nullable EventId survivor) {

	/**
	 * @throws IllegalArgumentException 작은 ID 가 큰 ID 보다 작지 않을 때
	 */
	public MergePair {
		Objects.requireNonNull(low, "low");
		Objects.requireNonNull(high, "high");
		if (low.value() >= high.value()) {
			throw new IllegalArgumentException("쌍은 (작은 ID, 큰 ID) 다: " + low.value() + ", " + high.value());
		}
	}

}
