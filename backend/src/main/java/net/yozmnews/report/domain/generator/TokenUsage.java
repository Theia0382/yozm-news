package net.yozmnews.report.domain.generator;

/**
 * 생성 한 번의 입력 · 출력 토큰 수 · docs/specs/report.md §4
 */
public record TokenUsage(int inputTokens, int outputTokens) {

	public TokenUsage {
		if (inputTokens < 0 || outputTokens < 0) {
			throw new IllegalArgumentException("토큰 수는 0 이상이다: " + inputTokens + ", " + outputTokens);
		}
	}

}
