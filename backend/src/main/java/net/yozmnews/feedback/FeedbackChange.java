package net.yozmnews.feedback;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.event.EventId;
import org.jspecify.annotations.Nullable;

/**
 * 이력 행 하나 — 소비자 조회 {@link EventFeedback} 의 값. docs/specs/feedback.md §3 · feedback-R1 · feedback-R8
 *
 * @param recorded 값을 기록한 사건 — 누른 때의 사건 ID
 * @param current 지금 사건 — 합쳐진 사건이면 남는 사건, 그대로면 {@code recorded} 와 같다. 지운 사건이면 없다
 * @param value 바뀐 뒤의 값 — 없음으로 되돌린 행은 {@link FeedbackValue#NONE}
 * @param digestId 누른 다이제스트의 ID({@code digest} 의 다이제스트 ID)
 * @param occurredAt 요청을 받은 시각
 */
public record FeedbackChange(EventId recorded, @Nullable EventId current, FeedbackValue value, long digestId,
		Instant occurredAt) {

	public FeedbackChange {
		Objects.requireNonNull(recorded, "recorded");
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(occurredAt, "occurredAt");
		if (digestId < 1) {
			throw new IllegalArgumentException("다이제스트 ID 는 1 이상이어야 한다: " + digestId);
		}
	}

}
