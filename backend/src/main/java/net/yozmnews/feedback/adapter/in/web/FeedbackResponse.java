package net.yozmnews.feedback.adapter.in.web;

import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.feedback.application.RecordOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 누르기 · 되돌리기의 결과 — 판정 결과 200. docs/specs/feedback.md §3 · feedback-R2 · feedback-R3
 *
 * @param result 기록 · 바뀌지 않음 · 받을 수 없는 항목
 * @param value 요청 뒤의 지금 값(없음이면 {@code NONE}) — 받을 수 없는 항목이면 없다
 */
record FeedbackResponse(Result result, @Nullable FeedbackValue value) {

	static FeedbackResponse of(RecordOutcome outcome) {
		return switch (outcome) {
			case RecordOutcome.Recorded recorded -> new FeedbackResponse(Result.RECORDED, recorded.value());
			case RecordOutcome.Unchanged unchanged -> new FeedbackResponse(Result.UNCHANGED, unchanged.value());
			case RecordOutcome.NotInDigest notInDigest -> new FeedbackResponse(Result.NOT_IN_DIGEST, null);
		};
	}

	/** 누르기 · 되돌리기 결과의 판별자 */
	enum Result {

		/** 값이 바뀌었다 */
		RECORDED,

		/** 이미 그 값이다 — 바꾸지 않았다 */
		UNCHANGED,

		/** 받을 수 없는 항목이다 — 그 사용자의 그 다이제스트에서 그 사건이 든 항목을 찾지 못했다 */
		NOT_IN_DIGEST

	}

}
