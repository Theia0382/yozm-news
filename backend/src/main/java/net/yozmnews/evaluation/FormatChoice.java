package net.yozmnews.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * 형식 Choice 질문과 그 선택지 — 파일에 적힌 순서 — docs/specs/evaluation.md §3
 */
public record FormatChoice(String question, List<FormatOption> options) {

	public FormatChoice {
		Objects.requireNonNull(question, "question");
		options = List.copyOf(options);
	}

}
