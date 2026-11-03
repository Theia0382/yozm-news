package net.yozmnews.evaluation;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 평가 요청 하나의 입력 토큰 — docs/specs/evaluation.md §3
 *
 * @param mean 평균 — 평가가 없으면 null
 * @param min 최소 — 평가가 없으면 null
 * @param max 최대 — 평가가 없으면 null
 * @param buckets 칸 — 작은 값부터 이어진다. 평가가 없으면 빈 목록
 */
public record TokenSummary(@Nullable Double mean, @Nullable Integer min, @Nullable Integer max,
		List<TokenBucket> buckets) {

	public TokenSummary {
		buckets = List.copyOf(buckets);
	}

}
