package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.event.IntakeFillResult;
import org.springframework.web.server.ResponseStatusException;

/**
 * 사건 묶기 채우기의 결과 — docs/specs/admin.md §3 · admin-R1.
 * <p>
 * {@code FILLED} 와 함께 기간에 처음 본 기사 수, 그 가운데 새로 대기에 넣은 수를 낸다. 기간이 범위 밖이면 결과가 아니라 400 이다.
 *
 * @param articles 기간에 처음 본 기사 수
 * @param enqueued 새로 사건 묶기 대기에 넣은 기사 수 — 배정이 있거나 이미 대기에 있는 기사는 세지 않는다
 */
record EventIntakeFillResponse(Outcome result, int articles, int enqueued) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws ResponseStatusException 400 — 기간이 범위 밖일 때
	 */
	static EventIntakeFillResponse of(IntakeFillResult result) {
		return switch (result) {
			case IntakeFillResult.Filled filled ->
				new EventIntakeFillResponse(Outcome.FILLED, filled.articles(), filled.enqueued());
			case IntakeFillResult.DaysOutOfRange outOfRange -> throw BackfillDays.outOfRange(outOfRange.max());
		};
	}

	enum Outcome {

		/** 기간의 기사를 대기에 채웠다 */
		FILLED

	}

}
