package net.yozmnews.event;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.platform.ChangeActor;
import org.jspecify.annotations.Nullable;

/**
 * 큰 사건 기준 이력의 한 행 — docs/specs/event.md §3 · event-R21.
 *
 * @param previous 바꾸기 전 값 — 처음 값이면 null
 * @param current 바꾼 뒤 값
 * @param actor 정한 행위자의 종류
 * @param changedAt 바꾼 시각
 */
public record BigEventCriteriaChange(@Nullable BigEventCriteria previous, BigEventCriteria current, ChangeActor actor,
		Instant changedAt) {

	public BigEventCriteriaChange {
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(changedAt, "changedAt");
	}

}
