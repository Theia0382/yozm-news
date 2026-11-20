package net.yozmnews.digest;

/**
 * 후보의 선정 이유 — 후보 행과 섹션 사건 행(항목이 다룬 사건)의 칸, 화면 항목의 배지. 공개 타입 · docs/specs/digest.md §3 · §6
 */
public enum SelectionReason {

	/** 구독 주제로 들어왔다 */
	SUBSCRIBED,

	/** 관심도 가중치로 순위가 올랐다 */
	INTEREST,

	/** 큰 스토리라인 슬롯으로 들어왔다 */
	MAJOR_STORYLINE

}
