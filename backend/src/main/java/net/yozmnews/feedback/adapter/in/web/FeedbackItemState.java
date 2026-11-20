package net.yozmnews.feedback.adapter.in.web;

import net.yozmnews.feedback.FeedbackValue;

/**
 * 항목 하나에 누른 값. docs/specs/feedback.md §3 · feedback-R7
 *
 * @param eventId 항목 키 — 그 항목의 첫 사건
 * @param value 좋아요 · 싫어요 — 항목 기록에 없음은 없다(feedback-R1)
 */
record FeedbackItemState(long eventId, Value value) {

	/**
	 * 항목 기록의 값으로 옮긴다.
	 * @throws IllegalArgumentException 없음({@link FeedbackValue#NONE})일 때 — 항목 기록에는 없음이 없다
	 */
	static FeedbackItemState of(long eventId, FeedbackValue value) {
		return new FeedbackItemState(eventId, switch (value) {
			case LIKE -> Value.LIKE;
			case DISLIKE -> Value.DISLIKE;
			case NONE -> throw new IllegalArgumentException("항목 기록에는 없음이 없다: " + eventId);
		});
	}

	/** 항목 기록의 값 — 좋아요 · 싫어요 */
	enum Value {

		/** 좋아요 */
		LIKE,

		/** 싫어요 */
		DISLIKE

	}

}
