package net.yozmnews.evaluation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 값의 칸 하나 — {@code [lower, upper)}, {@code upperInclusive} 면 {@code [lower, upper]} · docs/specs/evaluation.md §3
 */
public record ValueBucket(BigDecimal lower, BigDecimal upper, boolean upperInclusive, Share share) {

	public ValueBucket {
		Objects.requireNonNull(lower, "lower");
		Objects.requireNonNull(upper, "upper");
		Objects.requireNonNull(share, "share");
	}

}
