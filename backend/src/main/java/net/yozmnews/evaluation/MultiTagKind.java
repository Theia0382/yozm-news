package net.yozmnews.evaluation;

/**
 * 태그 기준 · 저장 기준이 있는 태그 종류 — 여러 태그가 붙는 주제 · 지역 — docs/specs/evaluation.md §3 · evaluation-R10.
 * <p>
 * 형식은 하나를 고르므로 태그 기준 · 저장 기준이 없다.
 */
public enum MultiTagKind {

	/** 주제 */
	TOPIC,

	/** 지역 */
	REGION

}
