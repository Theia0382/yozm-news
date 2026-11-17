package net.yozmnews.evaluation;

/**
 * 관계 판정의 답 — docs/specs/evaluation.md §3 · evaluation-R48.
 * <p>
 * 선언 순서는 부르는 쪽이 확률이 같을 때 쓰는 순서다 — 해당 없음이 맨 앞이다.
 */
public enum RelationAnswer {

	/** 해당 없음 — 별개의 사안이거나, 한쪽이 한 사안이 아니다 */
	NONE,

	/** 두 흐름이 같은 한 사안의 부분들이고, 어느 쪽도 다른 쪽 안에 들지 않는다 */
	SAME_MATTER,

	/** second 가 first 의 부분이다 */
	SECOND_PART_OF_FIRST,

	/** first 가 second 의 부분이다 */
	FIRST_PART_OF_SECOND,

	/** first 가 second 를 낳았다 */
	FIRST_LED_TO_SECOND,

	/** second 가 first 를 낳았다 */
	SECOND_LED_TO_FIRST

}
