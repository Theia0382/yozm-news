package net.yozmnews.digest.domain.candidate;

/**
 * 후보의 선정 이유 — 인용에 남는 노출 기록의 칸 · docs/specs/digest.md §6
 */
public enum SelectionReason {

	/** 구독 주제로 들어왔다 */
	SUBSCRIBED,

	/** 관심도 가중치로 순위가 올랐다 */
	INTEREST,

	/** 큰 스토리라인 슬롯으로 들어왔다 */
	MAJOR_STORYLINE

}
