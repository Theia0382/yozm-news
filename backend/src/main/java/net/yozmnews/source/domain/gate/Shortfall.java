package net.yozmnews.source.domain.gate;

import java.util.Objects;

import net.yozmnews.source.Criterion;

/**
 * 못 미친 기준 하나.
 *
 * @param actual 미리보기의 실제 값 — 기사 수이거나 비율
 * @param threshold 그 기준의 기준값
 */
public record Shortfall(Criterion criterion, double actual, double threshold) {

	public Shortfall {
		Objects.requireNonNull(criterion, "criterion");
	}

}
