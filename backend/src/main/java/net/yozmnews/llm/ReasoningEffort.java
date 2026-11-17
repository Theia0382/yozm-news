package net.yozmnews.llm;

/**
 * 생성 호출의 추론 강도 — docs/specs/llm.md §3.
 * <p>
 * 과제를 아는 부르는 쪽이 고르고, 관문이 공급자의 값으로 옮긴다. 강도는 출력 토큰 · 소요 시간 · 사용자 비용을 함께 바꾼다. 추론을 끄는 값은
 * 없다 — 끌 수 있는지가 모델마다 다르다.
 */
public enum ReasoningEffort {

	/** 낮음 */
	LOW,

	/** 보통 */
	MEDIUM,

	/** 높음 */
	HIGH

}
