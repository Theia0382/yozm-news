package net.yozmnews.feedback.application;

import java.util.Objects;

import net.yozmnews.feedback.FeedbackValue;

/**
 * 누르기 · 되돌리기의 결과 — docs/specs/feedback.md §4 · feedback-R2 · feedback-R3.
 */
public sealed interface RecordOutcome {

	/**
	 * 값이 바뀌었다 — 항목 기록이나 항목의 사건 하나 이상의 현재 상태를 쓰고, 바뀐 사건마다 이력 한 줄을 더했다.
	 * @param value 지금 값
	 */
	record Recorded(FeedbackValue value) implements RecordOutcome {

		public Recorded {
			Objects.requireNonNull(value, "value");
		}

	}

	/**
	 * 항목 기록과 항목의 사건이 모두 이미 그 값이다 — 아무것도 쓰지 않았다.
	 * @param value 지금 값
	 */
	record Unchanged(FeedbackValue value) implements RecordOutcome {

		public Unchanged {
			Objects.requireNonNull(value, "value");
		}

	}

	/** 받을 수 없는 항목이다 — 그 사용자의 그 다이제스트에서 그 사건이 든 항목을 찾지 못했다. 아무것도 쓰지 않았다. */
	record NotInDigest() implements RecordOutcome {
	}

}
