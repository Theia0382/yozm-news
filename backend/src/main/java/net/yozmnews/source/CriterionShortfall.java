package net.yozmnews.source;

import java.util.Objects;

/**
 * 못 미친 활성화 기준 하나 — 실제 값(기사 수이거나 비율)과 기준값 — docs/specs/source.md §3
 */
public record CriterionShortfall(Criterion criterion, double actual, double threshold) {

	public CriterionShortfall {
		Objects.requireNonNull(criterion, "criterion");
	}

}
