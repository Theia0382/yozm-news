package net.yozmnews.storyline;

/**
 * 쌍을 마지막으로 물은 결말 — 물은 기록에 남고 운영자 조회가 낸다 · docs/specs/storyline.md §3 · storyline-R29 · storyline-R30.
 * <p>
 * {@link #NOTHING_TO_ASK} · {@link #REJECTED} 는 판정 원본이 남지 않은 결말이다.
 */
public enum RelationOutcome {

	/** 맺었다 */
	LINKED,

	/** 가장 높은 답이 해당 없음이었다 */
	NOT_RELATED,

	/** 확률 기준 미만이었다 */
	LOW_PROBABILITY,

	/** 한 사안 기준 미만이었다 */
	NOT_ONE_MATTER,

	/** 넣으면 순환이 됐다 — 둘째 상위에도 걸리면 이것이다 */
	CYCLE,

	/** 하위에 이미 다른 상위가 있었다 */
	SECOND_PARENT,

	/** 한쪽의 서술이 남지 않아 평가기를 부르지 않았다 — 판정 원본이 없다 */
	NOTHING_TO_ASK,

	/** 평가기가 입력을 거부했다 — 판정 원본이 없다 */
	REJECTED

}
