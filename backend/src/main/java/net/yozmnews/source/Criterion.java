package net.yozmnews.source;

/**
 * 활성화 기준의 종류 — docs/specs/source.md §3. 기준값과 판정의 정본은 {@code domain/gate} 의 {@code ActivationCriteria}(source-R9)
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
