package net.yozmnews.digest.domain.generator;

import java.util.Objects;

/**
 * 생성기의 출력 원문과 사용량 — docs/specs/digest.md §4
 */
public record GeneratedOutput(String text, TokenUsage usage) {

	public GeneratedOutput {
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(usage, "usage");
	}

}
