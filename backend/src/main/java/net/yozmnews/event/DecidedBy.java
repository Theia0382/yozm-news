package net.yozmnews.event;

/** 배정을 정한 것 — 배정 행의 {@code decided_by} */
public enum DecidedBy {

	/** 산식 — 위 기준 이상이거나, 띠에 판정한 후보가 없다 */
	FORMULA,

	/** 사건 소속 판정 — 띠의 후보를 판정 확률로 붙였거나, 판정한 후보가 모두 확률 기준 미만이다 */
	EVALUATOR,

	/** 판정 실패 — 일시 실패가 상한에 이르렀거나 평가기가 기사를 거부해 새 사건으로 끝냈다 */
	JUDGEMENT_FAILED

}
