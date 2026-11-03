package net.yozmnews.evaluation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 확률 칸 하나 — {@code [lower, upper)}, {@code upperInclusive} 면 {@code [lower, upper]} · docs/specs/evaluation.md §3
 *
 * @param count 그 칸에 든 확률 수
 */
public record ProbabilityBucket(BigDecimal lower, BigDecimal upper, boolean upperInclusive, long count,
		StoredRange stored) {

	public ProbabilityBucket {
		Objects.requireNonNull(lower, "lower");
		Objects.requireNonNull(upper, "upper");
		Objects.requireNonNull(stored, "stored");
	}

}
