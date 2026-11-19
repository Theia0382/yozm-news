package net.yozmnews.platform;

/** 측정용 시계를 넘기지 않은 까닭 — docs/specs/platform.md §3 */
public enum AdvanceRefusal {

	/** 넘길 길이와 도착 시각을 둘 다 받았다 */
	BOTH_GIVEN,
	/** 넘길 길이도 도착 시각도 받지 않았다 */
	NONE_GIVEN,
	/** 넘길 길이가 0 이하다 */
	NOT_POSITIVE,
	/** 도착 시각이 지금보다 뒤가 아니다 */
	NOT_AHEAD

}
