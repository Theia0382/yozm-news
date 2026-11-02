package net.yozmnews.evaluation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 한 태그 종류의 두 기준 — docs/specs/evaluation.md §3 · evaluation-R10.
 *
 * @param tagThreshold 태그 기준 — 저장된 확률이 이 값 이상이면 태그가 붙는다
 * @param storeThreshold 저장 기준 — 평가 때 이 값 이상인 확률만 저장한다
 */
public record TagThresholds(MultiTagKind kind, BigDecimal tagThreshold, BigDecimal storeThreshold) {

	public TagThresholds {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(tagThreshold, "tagThreshold");
		Objects.requireNonNull(storeThreshold, "storeThreshold");
	}

}
