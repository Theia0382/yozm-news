package net.yozmnews.source.domain.profile;

/**
 * 프로필 문제의 종류. 운영자에게 보이는 문구는 {@link ProfileProblem#message()} 가 구체적으로 담는다.
 */
public enum ProblemCode {

	/** 텍스트가 길이 상한을 넘는다 */
	TOO_LARGE,
	/** YAML 문법 오류 */
	SYNTAX_ERROR,
	/** 최상위가 매핑이 아니다 */
	NOT_A_MAPPING,
	/** 문서가 둘 이상이다 */
	MULTIPLE_DOCUMENTS,
	/** 형식에 없는 키 */
	UNKNOWN_KEY,
	/** 같은 매핑 안에 같은 키가 또 있다 */
	DUPLICATE_KEY,
	/** YAML 별칭({@code *name}) */
	ALIAS_NOT_ALLOWED,
	/** YAML 앵커({@code &name}) */
	ANCHOR_NOT_ALLOWED,
	/** YAML 명시 태그({@code !!str}, {@code !foo}) */
	TAG_NOT_ALLOWED,
	/** 값이 비어 있다 — {@code null} 이거나 값을 적지 않았다({@code ~} 는 문자열이다) */
	NULL_NOT_ALLOWED,
	/** 값의 종류가 틀렸다(문자열 자리에 목록, 정수 자리에 문자열 · 범위 밖 정수 등) */
	WRONG_TYPE,
	/** 필수 항목이 없다 */
	MISSING_REQUIRED,
	/** 공백뿐인 문자열 */
	BLANK,
	/** 절대 URL 이 아니거나 스킴이 http · https 가 아니다. 이름공간이면 절대 URI 가 아니다 */
	INVALID_URL,
	/** 정규형 BCP 47 언어 태그가 아니거나 언어가 ISO 639-1 이 아니다 */
	INVALID_LANGUAGE,
	/** 피드 식별자 형식이 틀렸다 */
	INVALID_FEED_KEY,
	/** 피드 식별자가 겹친다 */
	DUPLICATE_FEED_KEY,
	/** 피드 URL 이 겹친다 */
	DUPLICATE_FEED_URL,
	/** 피드가 하나도 없다 */
	NO_FEEDS,
	/** 수집 주기가 하한보다 짧다 */
	POLL_INTERVAL_TOO_SHORT,
	/** 알 수 없는 사전 보정 규칙 */
	UNKNOWN_PRE_CORRECTION,
	/** 같은 사전 보정 규칙이 두 번 있다 */
	DUPLICATE_PRE_CORRECTION,
	/** 지원하지 않는 문자 인코딩 */
	INVALID_CHARSET,
	/** 알 수 없는 기사 항목 */
	UNKNOWN_ITEM_FIELD,
	/** 요소 이름이나 이름공간 접두사의 형식이 틀렸다 */
	INVALID_ELEMENT_NAME,
	/** 정의되지 않은 이름공간 접두사 */
	UNKNOWN_NAMESPACE_PREFIX,
	/** 기본 이름공간 접두사를 다시 정의했다 */
	PREFIX_REDEFINED,
	/** 같은 항목의 읽을 요소 목록 안에 같은 값이 또 있다 */
	DUPLICATE_VALUE_SOURCE,
	/** 날짜 형식 패턴이 틀렸거나 날짜와 시각(분까지)을 담지 않는다 */
	INVALID_DATE_FORMAT,
	/** 알 수 없는 시간대 */
	INVALID_TIME_ZONE,
	/** 이용조건 검토일이 ISO 날짜가 아니다 */
	INVALID_TERMS_REVIEWED_AT

}
