package net.yozmnews.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * 중요도 Score 질문과 수준별 기준 — 수준 순 — docs/specs/evaluation.md §3
 */
public record ImportanceScale(String question, List<ImportanceLevel> levels) {

	/** 가장 낮은 중요도 수준 — 정본(tag-R7) */
	public static final int MIN_LEVEL = 1;

	/** 가장 높을 수 있는 중요도 수준 — 정본(tag-R7). 체계마다 몇 수준을 쓰는지는 tag-R7 이 정한다 */
	public static final int MAX_LEVEL = 5;

	public ImportanceScale {
		Objects.requireNonNull(question, "question");
		levels = List.copyOf(levels);
	}

}
