package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.tag.EvaluationListing;
import net.yozmnews.tag.EvaluationReview;
import org.jspecify.annotations.Nullable;

/**
 * 평가 목록 한 번(평가 시각 · ID 내림차순)과 거를 태그 수의 상한 — docs/specs/admin.md §3 · tag-R33. 상한은
 * {@link EvaluationReview#MAX_FILTER_TAGS} 에서 읽는다.
 *
 * @param nextEvaluatedAt 이어 받을 커서의 평가 시각 — 더 없으면 null
 * @param nextId 이어 받을 커서의 평가 ID — 더 없으면 null
 * @param maxTags 거를 태그 수의 상한 — 같은 태그는 한 번으로 센다
 */
record EvaluationListResponse(long version, Instant from, Instant to, List<EvaluationRowResponse> rows,
		@Nullable Instant nextEvaluatedAt, @Nullable Long nextId, int maxTags) {

	static EvaluationListResponse of(EvaluationListing listing, List<EvaluationRowResponse> rows) {
		return new EvaluationListResponse(listing.version().value(), listing.from(), listing.to(), rows,
				(listing.next() == null) ? null : listing.next().evaluatedAt(),
				(listing.next() == null) ? null : listing.next().id().value(), EvaluationReview.MAX_FILTER_TAGS);
	}

}
