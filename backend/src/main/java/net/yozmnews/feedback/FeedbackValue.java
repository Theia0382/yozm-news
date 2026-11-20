package net.yozmnews.feedback;

/**
 * 다이제스트 항목에 누르고 그 항목의 사건마다 기록하는 값 — docs/specs/feedback.md §3 · feedback-R1.
 */
public enum FeedbackValue {

	/** 좋아요 */
	LIKE,

	/** 싫어요 */
	DISLIKE,

	/** 없음 — 현재 상태 · 항목 기록에는 없고(행이 없다) 이력과 요청에만 있다 */
	NONE

}
