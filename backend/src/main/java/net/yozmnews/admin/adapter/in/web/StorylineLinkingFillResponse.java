package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.storyline.LinkingFillResult;
import org.springframework.web.server.ResponseStatusException;

/**
 * 스토리라인 잇기 채우기의 결과 — docs/specs/admin.md §3 · admin-R1.
 * <p>
 * {@code FILLED} 와 함께 기간에 마지막 기사가 있는 사건 수, 그 가운데 새로 잇기 대기에 넣은 수를 낸다. 기간이 범위 밖이면 결과가 아니라
 * 400 이다.
 *
 * @param events 기간에 마지막 기사가 있는 사건 수
 * @param enqueued 새로 잇기 대기에 넣은 사건 수 — 이미 대기에 있던 사건은 세지 않는다
 */
record StorylineLinkingFillResponse(Outcome result, int events, int enqueued) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws ResponseStatusException 400 — 기간이 범위 밖일 때
	 */
	static StorylineLinkingFillResponse of(LinkingFillResult result) {
		return switch (result) {
			case LinkingFillResult.Filled filled ->
				new StorylineLinkingFillResponse(Outcome.FILLED, filled.events(), filled.enqueued());
			case LinkingFillResult.DaysOutOfRange outOfRange -> throw BackfillDays.outOfRange(outOfRange.max());
		};
	}

	enum Outcome {

		/** 기간의 사건을 잇기 대기에 채웠다 */
		FILLED

	}

}
