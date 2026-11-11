package net.yozmnews.tag;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 한 태그 종류의 확률 분포 — docs/specs/tag.md §3
 *
 * @param storeThreshold 지금 저장 기준 — 형식은 저장 기준이 없어 null
 * @param storeThresholdChanged 집계 기간 안 평가 시각의 저장 바닥이 둘 이상인가 — 그러면 저장 기준 근처 칸은 평가마다 다른 기준으로
 * 저장됐다. 형식은 저장 기준이 없어 거짓
 * @param buckets 칸 — 낮은 확률부터
 */
public record ProbabilityHistogram(@Nullable BigDecimal storeThreshold, boolean storeThresholdChanged,
		List<ProbabilityBucket> buckets) {

	public ProbabilityHistogram {
		buckets = List.copyOf(buckets);
	}

}
