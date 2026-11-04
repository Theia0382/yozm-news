package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.evaluation.EvaluationListing;
import org.jspecify.annotations.Nullable;

/**
 * 평가 목록 한 번 — 평가 시각 · ID 내림차순 · docs/specs/admin.md §3 · evaluation-R33.
 *
 * @param nextEvaluatedAt 이어 받을 커서의 평가 시각 — 더 없으면 null
 * @param nextId 이어 받을 커서의 평가 ID — 더 없으면 null
 */
record EvaluationListResponse(long version, Instant from, Instant to, List<EvaluationRowResponse> rows,
		@Nullable Instant nextEvaluatedAt, @Nullable Long nextId) {

	static EvaluationListResponse of(EvaluationListing listing, List<EvaluationRowResponse> rows) {
		return new EvaluationListResponse(listing.version().value(), listing.from(), listing.to(), rows,
				(listing.next() == null) ? null : listing.next().evaluatedAt(),
				(listing.next() == null) ? null : listing.next().id().value());
	}

}
