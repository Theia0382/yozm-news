package net.yozmnews.storyline.domain.relation;

/**
 * 쌍을 물은 결말 — 물은 기록에 남긴다 · docs/specs/storyline.md §4 · storyline-R30.
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

	/** 넣으면 순환이 됐다 */
	CYCLE,

	/** 하위에 이미 다른 상위가 있었다 */
	SECOND_PARENT,

	/** 한쪽의 서술이 남지 않아 평가기를 부르지 않았다 */
	NOTHING_TO_ASK,

	/** 평가기가 입력을 거부했다 */
	REJECTED;

	/** 판정의 결말 */
	public static RelationOutcome of(RelationRule.Decision decision) {
		return switch (decision) {
			case RelationRule.Decision.Link link -> LINKED;
			case RelationRule.Decision.None none -> switch (none.reason()) {
				case NOT_RELATED -> NOT_RELATED;
				case LOW_PROBABILITY -> LOW_PROBABILITY;
				case NOT_ONE_MATTER -> NOT_ONE_MATTER;
				case CYCLE -> CYCLE;
				case SECOND_PARENT -> SECOND_PARENT;
			};
		};
	}

}
