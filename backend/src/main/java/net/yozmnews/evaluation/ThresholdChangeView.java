package net.yozmnews.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 두 기준 이력 한 행 — docs/specs/evaluation.md §3 · evaluation-R13.
 *
 * @param previousTagThreshold 이전 태그 기준 — 처음 값이면 null
 * @param previousStoreThreshold 이전 저장 기준 — 처음 값이면 null
 */
public record ThresholdChangeView(MultiTagKind kind, @Nullable BigDecimal previousTagThreshold,
		@Nullable BigDecimal previousStoreThreshold, BigDecimal tagThreshold, BigDecimal storeThreshold,
		ChangeActor actor, Instant changedAt) {

	public ThresholdChangeView {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(tagThreshold, "tagThreshold");
		Objects.requireNonNull(storeThreshold, "storeThreshold");
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(changedAt, "changedAt");
	}

}
