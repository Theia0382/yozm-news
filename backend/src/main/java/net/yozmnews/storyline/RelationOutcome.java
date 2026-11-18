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

	/**
	 * 나란한 흐름으로 판정돼 한 상위 아래 묶었다 — 상위를 열었거나, 하위를 가진 상위의 하위로 두었거나, 이미 같은 상위의 하위였다
	 */
	GROUPED,

	/**
	 * 나란한 흐름으로 판정됐고 한쪽이 상위 아래 있거나 하위를 가진 상위여서, 다른 흐름과 그 상위의 하위들(다른 하위가 없으면 그 상위)의
	 * 쌍을 넘겨 물었다
	 */
	REFERRED,

	/**
	 * 나란한 흐름으로 판정됐으나 묶지 않았다 — 상위를 열 만큼 평가를 통과한 사건이 없었거나, 다른 흐름이 그 상위의 어느 하위와 나란한
	 * 흐름이 아니었다
	 */
	UNGROUPED,

	/** 같은 사안의 조각으로 판정됐으나 합치지 않았다 — 두 스토리라인 사이의 관계는 지웠다 */
	UNMERGED,

	/** 한쪽의 서술이 남지 않아 평가기를 부르지 않았다 — 판정 원본이 없다 */
	NOTHING_TO_ASK,

	/** 평가기가 입력을 거부했다 — 판정 원본이 없다 */
	REJECTED

}
