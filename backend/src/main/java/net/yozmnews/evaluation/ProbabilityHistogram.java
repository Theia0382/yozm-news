package net.yozmnews.evaluation;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 한 태그 종류의 확률 분포 — docs/specs/evaluation.md §3
 *
 * @param storeThreshold 지금 저장 기준 — 형식은 저장 기준이 없어 null
 * @param storeThresholdChanged 그 버전 · 그 태그 종류의 저장 기준이 바뀐 적이 있는가 — 바뀌었으면 칸마다 저장된 범위가 평가마다 달랐다
 * @param buckets 칸 — 낮은 확률부터
 */
public record ProbabilityHistogram(@Nullable BigDecimal storeThreshold, boolean storeThresholdChanged,
		List<ProbabilityBucket> buckets) {

	public ProbabilityHistogram {
		buckets = List.copyOf(buckets);
	}

}
