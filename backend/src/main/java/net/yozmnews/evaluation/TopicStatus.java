package net.yozmnews.evaluation;

/**
 * 기사의 주제 판정 — 조회 시점에 그 평가 버전의 지금 태그 기준 · 저장 기준으로 정한다 · docs/specs/evaluation.md §3
 */
public enum TopicStatus {

	/** 태그 기준을 넘은 주제 태그가 있다 */
	TAGGED,

	/** 태그 기준을 넘은 주제 태그가 없어, 저장 기준 이상인 가장 높은 확률의 태그 하나를 붙였다 — 후보 선별이 가중치를 낮춘다 */
	LOW_CONFIDENCE,

	/** 저장 기준 이상인 주제 태그가 없다 */
	UNCLASSIFIED

}
