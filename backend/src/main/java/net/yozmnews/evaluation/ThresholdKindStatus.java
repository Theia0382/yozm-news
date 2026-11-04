package net.yozmnews.evaluation;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * 한 태그 종류의 기준 상태 — docs/specs/evaluation.md §3 · evaluation-R34.
 *
 * @param pastMaxStoreThreshold 그 버전 · 그 태그 종류의 이력(처음 값 포함)에 있는 저장 기준의 최댓값
 * @param conditions 어긋난 상태 — 없으면 빈 집합
 */
public record ThresholdKindStatus(TagThresholds current, BigDecimal pastMaxStoreThreshold,
		Set<ThresholdCondition> conditions) {

	public ThresholdKindStatus {
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(pastMaxStoreThreshold, "pastMaxStoreThreshold");
		conditions = Set.copyOf(conditions);
	}

}
