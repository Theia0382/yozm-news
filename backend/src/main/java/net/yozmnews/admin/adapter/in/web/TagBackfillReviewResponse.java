package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.evaluation.TagBackfillAdministration;
import net.yozmnews.evaluation.TagBackfillReviewResult;
import net.yozmnews.evaluation.TagBackfillVersionCount;
import org.springframework.web.server.ResponseStatusException;

/**
 * 빠진 태그 채움 확인의 결과 — docs/specs/admin.md §3 · admin-R1.
 * <p>
 * {@code READY} 면 덮을 평가 시각 범위({@code from} 포함 · {@code to} 제외)와 평가 수, 평가가 있는 버전마다 수(버전 번호 순)가 있다. 기간이
 * 범위 밖이면 결과가 아니라 400 이다.
 */
record TagBackfillReviewResponse(Outcome result, Instant from, Instant to, long evaluations,
		List<VersionCount> versions) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws ResponseStatusException 400 — 기간이 범위 밖일 때
	 */
	static TagBackfillReviewResponse of(TagBackfillReviewResult result) {
		return switch (result) {
			case TagBackfillReviewResult.Ready ready -> new TagBackfillReviewResponse(Outcome.READY, ready.from(),
					ready.to(), ready.evaluations(), ready.versions().stream().map(VersionCount::of).toList());
			case TagBackfillReviewResult.DaysOutOfRange outOfRange -> throw BackfillDays.outOfRange(TagBackfillAdministration.MAX_DAYS);
		};
	}

	enum Outcome {

		/** 채울 수 있다 */
		READY

	}

	/** 버전 하나의 평가 수 — ID 는 JSON 수다(admin-R1) */
	record VersionCount(long id, int number, long evaluations) {

		static VersionCount of(TagBackfillVersionCount count) {
			return new VersionCount(count.version().value(), count.number(), count.evaluations());
		}

	}

}
