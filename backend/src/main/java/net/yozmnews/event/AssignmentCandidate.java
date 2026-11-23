package net.yozmnews.event;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 배정과 함께 남긴 저장 후보 하나 · docs/specs/event.md §3 · event-R12.
 *
 * @param event 배정 때의 후보 사건
 * @param current 그 사건의 지금 사건 — 합쳐졌으면 남는 사건, 아니면 {@code event}
 * @param score 그 후보의 점수
 * @param probability 그 후보의 사건 소속 판정 확률 — 묻지 않았으면 null
 */
public record AssignmentCandidate(EventId event, EventId current, double score, @Nullable Double probability) {

	public AssignmentCandidate {
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(current, "current");
	}

}
