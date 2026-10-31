package net.yozmnews.source;

/**
 * 피드 문서 전체를 읽지 못한 이유
 */
public enum DocumentProblemKind {

	/** 문서가 선언한 문자 인코딩을 지원하지 않는다 */
	UNSUPPORTED_ENCODING,
	/** XML 형식이 틀렸다 */
	MALFORMED_XML,
	/** DTD 선언이나 선언되지 않은 엔티티가 있다 — 피드의 DTD 는 해석하지 않는다 */
	DTD_DECLARATION,
	/** XML 이지만 RSS · Atom 피드가 아니다 */
	NOT_A_FEED

}
