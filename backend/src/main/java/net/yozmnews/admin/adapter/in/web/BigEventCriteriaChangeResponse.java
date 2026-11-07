package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.event.BigEventCriteriaChangeResult;
import org.jspecify.annotations.Nullable;

/**
 * 큰 사건 기준 변경의 결과 — docs/specs/admin.md §3 · admin-R1 · event-R21.
 * <p>
 * {@code CHANGED} 면 이전 값과 새 값, {@code UNCHANGED} 면 지금 값이 있다. 범위 밖 값은 결과가 아니라 400 이다.
 */
record BigEventCriteriaChangeResponse(Outcome result, @Nullable BigEventCriteriaResponse previous,
		BigEventCriteriaResponse current) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws org.springframework.web.server.ResponseStatusException 400 — 값이 범위 밖일 때
	 */
	static BigEventCriteriaChangeResponse of(BigEventCriteriaChangeResult result) {
		return switch (result) {
			case BigEventCriteriaChangeResult.Changed changed -> new BigEventCriteriaChangeResponse(Outcome.CHANGED,
					BigEventCriteriaResponse.of(changed.previous()), BigEventCriteriaResponse.of(changed.current()));
			case BigEventCriteriaChangeResult.Unchanged unchanged -> new BigEventCriteriaChangeResponse(
					Outcome.UNCHANGED, null, BigEventCriteriaResponse.of(unchanged.current()));
			case BigEventCriteriaChangeResult.OutOfRange outOfRange -> throw BigEventCriteriaProblem.outOfRange();
		};
	}

	enum Outcome {

		/** 바꿨다 */
		CHANGED,
		/** 지금 값과 같아 바꾸지 않았다 */
		UNCHANGED

	}

}
