package net.yozmnews.user.domain.subscription;

import java.time.Instant;
import java.util.Objects;

/**
 * 구독 이력 행의 순서 키 — 시각과 행 ID. 내 구독 이력의 다음 쪽은 이 키보다 앞선 행부터다. docs/specs/user.md §5 · user-R63
 *
 * @param occurredAt 기록한 시각
 * @param id 이력 행 ID — 1 이상
 */
public record SubscriptionChangeCursor(Instant occurredAt, long id) {

	public SubscriptionChangeCursor {
		Objects.requireNonNull(occurredAt, "occurredAt");
		if (id < 1) {
			throw new IllegalArgumentException("이력 행 ID 는 1 이상이다: " + id);
		}
	}

}
