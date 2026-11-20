package net.yozmnews.feedback.domain.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackValue;
import org.junit.jupiter.api.Test;

/**
 * 풀린 사건의 값 — 지금 사건 하나로 풀리는 기록한 사건들의 마지막 이력 행 가운데 행 ID 가 가장 큰 것의 값.
 * <p>
 * 지키는 규칙: feedback-R8
 */
class MergedValueTests {

	private static final EventId A = new EventId(1);

	private static final EventId B = new EventId(2);

	private static final EventId C = new EventId(3);

	@Test
	void theLastPressAmongTheEventsResolvingToOneWins() {
		// a · b 가 b 로 풀린다 — a 의 싫어요(행 10)보다 b 의 좋아요(행 20)가 나중이다
		Map<EventId, EventId> currentOf = Map.of(A, B, B, B);

		assertThat(MergedValue.of(List.of(new LastChange(A, 10, FeedbackValue.DISLIKE),
				new LastChange(B, 20, FeedbackValue.LIKE)), currentOf))
			.containsExactly(Map.entry(B, FeedbackValue.LIKE));
		assertThat(MergedValue.of(List.of(new LastChange(B, 20, FeedbackValue.LIKE),
				new LastChange(A, 30, FeedbackValue.DISLIKE)), currentOf))
			.containsExactly(Map.entry(B, FeedbackValue.DISLIKE));
	}

	@Test
	void aLastRevertWinsOverAnEarlierValueOfAnotherEvent() {
		Map<EventId, EventId> currentOf = Map.of(A, B, B, B);

		assertThat(MergedValue.of(List.of(new LastChange(A, 10, FeedbackValue.DISLIKE),
				new LastChange(B, 20, FeedbackValue.NONE)), currentOf))
			.containsExactly(Map.entry(B, FeedbackValue.NONE));
	}

	@Test
	void anUnmergedEventKeepsItsOwnValueAndUnresolvedRowsDropOut() {
		// 대조 — 풀리지 않는 사건(C)의 행은 버리고, 저마다 지금 사건인 A · B 는 자기 값이다
		Map<EventId, EventId> currentOf = Map.of(A, A, B, B);

		assertThat(MergedValue.of(List.of(new LastChange(A, 10, FeedbackValue.DISLIKE),
				new LastChange(B, 5, FeedbackValue.LIKE), new LastChange(C, 40, FeedbackValue.LIKE)), currentOf))
			.containsOnly(Map.entry(A, FeedbackValue.DISLIKE), Map.entry(B, FeedbackValue.LIKE));
	}

}
