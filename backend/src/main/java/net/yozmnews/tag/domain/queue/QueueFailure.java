package net.yozmnews.tag.domain.queue;

/**
 * 대기 행이 실패로 남은 까닭 — docs/specs/tag.md §5
 */
public enum QueueFailure {

	/** 평가기가 그 기사를 거부했다 — 다시 보내도 같다 */
	ARTICLE_REJECTED,

	/** 일시 실패가 시도 상한에 이르렀다 */
	RETRIES_EXHAUSTED

}
