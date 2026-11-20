package net.yozmnews.feedback.domain.record;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackValue;

/**
 * 저장된 이력 행 하나 — 기록한 사건 ID 그대로다. 지금 사건으로 푸는 일은 소비자 조회가 한다. docs/specs/feedback.md §4 · feedback-R1
 *
 * @param event 기록한 사건
 * @param value 바뀐 뒤의 값
 * @param digestId 누른 다이제스트의 ID
 * @param occurredAt 요청을 받은 시각
 */
public record HistoryRow(EventId event, FeedbackValue value, long digestId, Instant occurredAt) {

	public HistoryRow {
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(occurredAt, "occurredAt");
	}

}
