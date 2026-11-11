package net.yozmnews.tag;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * 한 태그 종류의 기준 상태 — docs/specs/tag.md §3 · tag-R36.
 *
 * @param pastMaxStoreThreshold 지난 저장 기준 최댓값 — 이력과 끝난 빠진 태그 채움으로 계산한 저장 바닥의 최댓값(tag-R32)
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
