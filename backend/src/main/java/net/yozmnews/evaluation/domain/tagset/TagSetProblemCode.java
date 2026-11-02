package net.yozmnews.evaluation.domain.tagset;

import net.yozmnews.platform.YamlProblemKind;

/**
 * 태그 체계 파일 문제의 종류 — docs/specs/evaluation.md §4 · evaluation-R1 · evaluation-R5 ~ evaluation-R8 · evaluation-R10 ·
 * evaluation-R22.
 * <p>
 * 앞 11개는 공통 YAML 문제와 같은 이름이고 {@link #of} 가 옮긴다. 사람에게 보이는 문구는 {@link TagSetProblem#message()} 가
 * 구체적으로 담는다.
 */
public enum TagSetProblemCode {

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

	/** YAML 별칭을 썼다 */
	ALIAS_NOT_ALLOWED,

	/** YAML 앵커를 썼다 */
	ANCHOR_NOT_ALLOWED,

	/** YAML 명시 태그를 썼다 */
	TAG_NOT_ALLOWED,

	/** 값이 비어 있다 */
	NULL_NOT_ALLOWED,

	/** 값의 종류가 틀렸다 */
	WRONG_TYPE,

	/** 필수 항목이 없다 */
	MISSING_REQUIRED,

	/** 문구가 공백뿐이다 */
	BLANK,

	/** 문구가 한 줄이 아니다 */
	MULTI_LINE,

	/** 문구가 길이 상한을 넘는다 */
	TOO_LONG,

	/** 태그 키 조각의 형식이 틀렸다 */
	INVALID_KEY,

	/** 같은 태그 종류 안에서 키가 겹친다 */
	DUPLICATE_KEY_IN_KIND,

	/** 주제 대분류가 하나도 없다 */
	NO_TOPICS,

	/** 대분류에 중분류가 하나도 없다 */
	NO_SUBTOPICS,

	/** 지역이 하나도 없다 */
	NO_REGIONS,

	/** 형식이 둘보다 적다 */
	TOO_FEW_FORMATS,

	/** 중요도 수준이 1 ~ 5 하나씩이 아니다 */
	INVALID_IMPORTANCE_LEVELS,

	/** 질문 틀의 자리표시자가 정확히 하나가 아니다 */
	INVALID_TEMPLATE,

	/** 형식 · 중요도 질문에 자리표시자가 있다 */
	PLACEHOLDER_NOT_ALLOWED,

	/** 기준 값이 0 ~ 1 · 소수 셋째 자리 밖이다 */
	INVALID_THRESHOLD,

	/** 계보 항목의 태그 종류 · 계보 종류가 정해진 값이 아니다 */
	INVALID_LINEAGE_VALUE,

	/** 계보 항목의 이전 키 · 새 키 수가 계보 종류와 맞지 않는다 */
	INVALID_LINEAGE_COUNT,

	/** 계보 항목 한 목록 안에 같은 키가 또 있거나, 이름 변경의 이전 키와 새 키가 같다 */
	DUPLICATE_LINEAGE_KEY,

	/** 같은 태그 종류에서 한 키가 두 계보 항목의 이전 키(또는 새 키)다 */
	LINEAGE_KEY_REPEATED,

	/** 계보의 새 키가 이 원문의 같은 태그 종류에 없다 */
	LINEAGE_TARGET_MISSING,

	/** 주제 계보가 대분류와 중분류를 잇는다 — 같은 층끼리만 잇는다 */
	LINEAGE_LAYER_MISMATCH,

	/** 기본 체계 파일에 계보 절이 있다 */
	LINEAGE_IN_DEFAULT;

	/**
	 * 공통 YAML 문제의 종류를 같은 이름의 코드로 옮긴다 — 공통 종류가 늘면 이 {@code switch} 가 컴파일되지 않는다(ADR-0056).
	 */
	public static TagSetProblemCode of(YamlProblemKind kind) {
		return switch (kind) {
			case TOO_LARGE -> TOO_LARGE;
			case SYNTAX_ERROR -> SYNTAX_ERROR;
			case NOT_A_MAPPING -> NOT_A_MAPPING;
			case MULTIPLE_DOCUMENTS -> MULTIPLE_DOCUMENTS;
			case UNKNOWN_KEY -> UNKNOWN_KEY;
			case DUPLICATE_KEY -> DUPLICATE_KEY;
			case ALIAS_NOT_ALLOWED -> ALIAS_NOT_ALLOWED;
			case ANCHOR_NOT_ALLOWED -> ANCHOR_NOT_ALLOWED;
			case TAG_NOT_ALLOWED -> TAG_NOT_ALLOWED;
			case NULL_NOT_ALLOWED -> NULL_NOT_ALLOWED;
			case WRONG_TYPE -> WRONG_TYPE;
		};
	}

}
