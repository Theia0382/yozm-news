package net.yozmnews.llm;

/**
 * 공급자가 응답한 생성의 종료 사유 — docs/specs/llm.md §3.
 * <p>
 * 셋 모두 공급자가 정상으로 답한 응답이다(과금된다). 잘리거나 거절된 응답의 본문은 출력 스키마에 맞지 않을 수 있다.
 */
public enum GenerationStop {

	/** 끝까지 썼다 */
	COMPLETE,

	/** 출력 토큰 상한에 닿아 잘렸다 */
	TRUNCATED,

	/** 모델이 거절했다 */
	REFUSED

}
