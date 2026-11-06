package net.yozmnews.event.domain.assignment;

/** 판정의 때 — {@link AssignmentRule#decide} 가 띠 안의 확률 없는 후보를 어떻게 다룰지 정한다 */
public enum DecisionPhase {

	/** 처음 판정 — 확률 없는 띠 후보가 있으면 사건 소속 판정에 묻는다 */
	FIRST,

	/** 판정 확률을 채운 뒤 — 그래도 확률 없는 띠 후보는 붙일 수 없는 것으로 본다 */
	AFTER_JUDGEMENT,

	/** 재판정 — 저장값만 쓰고, 확률 없는 띠 후보가 있으면 지금 배정을 유지한다 */
	REJUDGE

}
