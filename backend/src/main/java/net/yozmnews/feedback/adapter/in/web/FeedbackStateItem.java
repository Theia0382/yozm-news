package net.yozmnews.feedback.adapter.in.web;

import net.yozmnews.feedback.FeedbackValue;

/**
 * 기사 하나의 현재 상태. docs/specs/feedback.md §3 · feedback-R7
 *
 * @param articleId 기사
 * @param value 좋아요 · 싫어요 — 현재 상태에 없음은 없다(feedback-R1)
 */
record FeedbackStateItem(long articleId, Value value) {

	/**
	 * 현재 상태의 값으로 옮긴다.
	 * @throws IllegalArgumentException 없음({@link FeedbackValue#NONE})일 때 — 현재 상태에는 없음이 없다
	 */
	static FeedbackStateItem of(long articleId, FeedbackValue value) {
		return new FeedbackStateItem(articleId, switch (value) {
			case LIKE -> Value.LIKE;
			case DISLIKE -> Value.DISLIKE;
			case NONE -> throw new IllegalArgumentException("현재 상태에는 없음이 없다: " + articleId);
		});
	}

	/** 현재 상태의 값 — 좋아요 · 싫어요 */
	enum Value {

		/** 좋아요 */
		LIKE,

		/** 싫어요 */
		DISLIKE

	}

}
