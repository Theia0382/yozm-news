package net.yozmnews.platform;

/**
 * YAML 공통 문제의 종류 — docs/specs/platform.md §3 · platform-R17.
 * <p>
 * 어느 문서에서나 같은 문제다. 문서별 문제는 각 모듈이 자기 종류로 더하고, 이 종류를 자기 코드로 옮길 때는 {@code default} 없는 망라
 * {@code switch} 로 쓴다.
 */
public enum YamlProblemKind {

	/** 원문이 길이 상한을 넘는다 */
	TOO_LARGE,

	/** YAML 문법이 틀렸다 */
	SYNTAX_ERROR,

	/** 최상위가 매핑이 아니다 */
	NOT_A_MAPPING,

	/** 문서가 둘 이상이다 */
	MULTIPLE_DOCUMENTS,

	/** 형식에 없는 키다 */
	UNKNOWN_KEY,

	/** 같은 매핑 안에 같은 키가 또 있다 */
	DUPLICATE_KEY,

	/** YAML 별칭({@code *name})을 썼다 */
	ALIAS_NOT_ALLOWED,

	/** YAML 앵커({@code &name})를 썼다 */
	ANCHOR_NOT_ALLOWED,

	/** YAML 명시 태그({@code !!str}, {@code !foo})를 썼다 */
	TAG_NOT_ALLOWED,

	/** 값이 비어 있다 — {@code null} 이거나 값을 적지 않았다({@code ~} 는 문자열이다) */
	NULL_NOT_ALLOWED,

	/** 값의 종류가 틀렸다(문자열 자리에 목록, 정수 자리에 문자열 · 범위 밖 정수 등) */
	WRONG_TYPE

}
