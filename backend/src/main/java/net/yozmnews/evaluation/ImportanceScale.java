package net.yozmnews.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * 중요도 Score 질문과 수준별 기준 — 수준 순 — docs/specs/evaluation.md §3
 */
public record ImportanceScale(String question, List<ImportanceLevel> levels) {

	public ImportanceScale {
		Objects.requireNonNull(question, "question");
		levels = List.copyOf(levels);
	}

}
