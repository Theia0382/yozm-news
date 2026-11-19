package net.yozmnews.digest.domain.item;

/**
 * 항목의 종류 — 코드가 가르고 LLM 은 그 종류의 칸에 쓴다 · docs/specs/digest.md §5 · digest-R47
 */
public enum ItemKind {

	/** 주요 뉴스 — 제목 · 요약 */
	TOP_STORY,

	/** 헤드라인 — 제목 · 짧은 코멘트 */
	HEADLINE

}
