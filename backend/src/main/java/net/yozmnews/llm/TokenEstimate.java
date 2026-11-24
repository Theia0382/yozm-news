package net.yozmnews.llm;

/**
 * 입력 토큰의 어림 — 코드 포인트를 토큰으로 바꾸는 비율 · 함수의 정본 — docs/specs/llm.md §3 · llm-R4
 */
public final class TokenEstimate {

	/** 코드 포인트 하나의 토큰 어림 */
	public static final double TOKENS_PER_CODE_POINT = 1.0;

	private TokenEstimate() {
	}

	/**
	 * 코드 포인트 수의 토큰 어림 — 비율을 곱해 올린다
	 * @throws IllegalArgumentException 코드 포인트 수가 음수일 때
	 */
	public static int tokens(int codePoints) {
		if (codePoints < 0) {
			throw new IllegalArgumentException("코드 포인트 수가 음수다: " + codePoints);
		}
		return (int) Math.ceil(codePoints * TOKENS_PER_CODE_POINT);
	}

}
