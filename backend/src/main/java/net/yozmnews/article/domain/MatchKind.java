package net.yozmnews.article.domain;

/**
 * 판정 종류 — 엔트리의 {@code match_kind}. 그 피드의 항목이 기사를 새로 만들었는지, 어떤 판정으로 기존 기사에 붙었는지.
 */
public enum MatchKind {

	/** 기사를 새로 만들었다 */
	NEW,
	/** 정규 URL 이 기사의 URL 이나 다른 엔트리의 항목 URL 과 같다 */
	URL,
	/** 같은 피드에 같은 guid 의 엔트리가 있고, 그 엔트리가 붙은 기사와 제목 비교 키도 같다 */
	GUID,
	/** 다른 피드에서만 본 같은 소스의 기사와 제목 비교 키가 같고 발행 시각이 시간 창 안이다 */
	TITLE

}
