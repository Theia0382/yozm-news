package net.yozmnews.article.domain;

/**
 * 판정 종류 — 엔트리의 {@code match_kind} · docs/specs/article.md §2
 */
public enum MatchKind {

	/** 기사를 새로 만들었다 */
	NEW,
	/** 정규 URL 로 기존 기사에 붙었다 */
	URL,
	/** guid 로 기존 기사에 붙었다 */
	GUID,
	/** 제목으로 기존 기사에 붙었다 */
	TITLE

}
