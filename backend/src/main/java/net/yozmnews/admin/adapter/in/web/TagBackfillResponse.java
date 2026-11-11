package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.tag.TagBackfillAdministration;
import net.yozmnews.tag.TagBackfillRequestResult;
import org.springframework.web.server.ResponseStatusException;

/**
 * 빠진 태그 채움 요청의 결과 — docs/specs/admin.md §3 · admin-R1.
 * <p>
 * {@code REQUESTED} 면 기록한 채움, {@code ALREADY_OPEN} 이면 끝나지 않은 채움이 {@code run} 에 있다. 기간이 범위 밖이면 결과가 아니라
 * 400 이다.
 */
record TagBackfillResponse(Outcome result, TagBackfillRunResponse run) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws ResponseStatusException 400 — 기간이 범위 밖일 때
	 */
	static TagBackfillResponse of(TagBackfillRequestResult result) {
		return switch (result) {
			case TagBackfillRequestResult.Requested requested ->
				new TagBackfillResponse(Outcome.REQUESTED, TagBackfillRunResponse.of(requested.run()));
			case TagBackfillRequestResult.AlreadyOpen open ->
				new TagBackfillResponse(Outcome.ALREADY_OPEN, TagBackfillRunResponse.of(open.run()));
			case TagBackfillRequestResult.DaysOutOfRange outOfRange -> throw BackfillDays.outOfRange(TagBackfillAdministration.MAX_DAYS);
		};
	}

	enum Outcome {

		/** 채움 하나를 기록했다 */
		REQUESTED,
		/** 끝나지 않은 채움이 있어 아무것도 쓰지 않았다 */
		ALREADY_OPEN

	}

}
