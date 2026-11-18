package net.yozmnews.storyline;

/** 소속을 정한 것 — docs/specs/storyline.md §3 · storyline-R14 */
public enum LinkedBy {

	/** 잇기 규칙으로 붙였다(storyline-R10) */
	RULE,

	/** 짝으로 새 스토리라인을 열었다(storyline-R11) */
	PAIR,

	/** 합쳐진 사건의 소속을 남는 사건으로 옮겼다 — 점수 · 기준이 없다(storyline-R6) */
	MERGE,

	/** 같은 사안의 조각 위에 상위를 열 때, 잇기 규칙의 평가를 통과해 그 상위에 들었다 — 붙이기처럼 점수 · 기준을 갖는다 */
	GROUP

}
