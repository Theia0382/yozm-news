package net.yozmnews.source.domain.gate;

/**
 * 활성화 기준의 종류 — 기준값은 {@link ActivationCriteria}
 */
public enum Criterion {

	/** 기사 수 */
	ARTICLES,
	/** 항목 수 대비 건너뛴 항목 비율 */
	SKIPPED_ITEMS,
	/** 기사 수 대비 발행 시각을 해석한 기사 비율 */
	DATED_ARTICLES,
	/** 읽은 문자 수 대비 U+FFFD 로 바꾼 자리 비율 */
	REPLACED_CHARACTERS

}
