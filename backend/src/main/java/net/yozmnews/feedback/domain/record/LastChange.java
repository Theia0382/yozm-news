package net.yozmnews.feedback.domain.record;

import java.util.Objects;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackValue;

/**
 * 기록한 사건 하나의 마지막 이력 행 — docs/specs/feedback.md §4 · feedback-R4
 *
 * @param event 기록한 사건
 * @param rowId 이력 행 ID — 순서의 정본
 * @param value 그 행의 값 — 없음으로 되돌린 행은 {@link FeedbackValue#NONE}
 */
public record LastChange(EventId event, long rowId, FeedbackValue value) {

	public LastChange {
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(value, "value");
	}

}
