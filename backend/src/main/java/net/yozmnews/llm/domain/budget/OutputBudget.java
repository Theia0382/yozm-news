package net.yozmnews.llm.domain.budget;

import java.util.Objects;

import net.yozmnews.llm.ReasoningEffort;

/**
 * 공급자에 보내는 출력 토큰 상한의 정본 — docs/specs/llm.md llm-R23.
 * <p>
 * OpenAI · Anthropic 모두 출력 토큰 상한이 추론과 응답 본문을 합친 상한이다. 본문 몫은 부르는 쪽이 정하고, 추론 몫은 추론 강도마다의
 * 여유로 이 모듈이 더한다. 여유 셋은 측정 전의 잠정값이다 — 강도마다 모델이 실제로 쓴 추론 토큰을 재서 고친다.
 */
public final class OutputBudget {

	/** 낮은 강도의 추론 여유(토큰) */
	public static final int LOW_ALLOWANCE = 4_000;

	/** 보통 강도의 추론 여유(토큰) */
	public static final int MEDIUM_ALLOWANCE = 8_000;

	/** 높은 강도의 추론 여유(토큰) */
	public static final int HIGH_ALLOWANCE = 16_000;

	private OutputBudget() {
	}

	/**
	 * @param bodyTokens 본문 몫 — 1 이상
	 * @return 본문 몫 + 강도의 여유
	 */
	public static int total(int bodyTokens, ReasoningEffort effort) {
		Objects.requireNonNull(effort, "effort");
		if (bodyTokens < 1) {
			throw new IllegalArgumentException("본문 토큰은 1 이상이다 — " + bodyTokens);
		}
		int allowance = switch (effort) {
			case LOW -> LOW_ALLOWANCE;
			case MEDIUM -> MEDIUM_ALLOWANCE;
			case HIGH -> HIGH_ALLOWANCE;
		};
		return Math.addExact(bodyTokens, allowance);
	}

}
