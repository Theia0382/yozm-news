package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.event.BigEventCriteriaChange;
import org.jspecify.annotations.Nullable;

/**
 * 큰 사건 기준 이력의 한 행 — docs/specs/admin.md §3 · event-R21.
 *
 * @param previous 바꾸기 전 값 — 처음 값이면 null
 */
record BigEventCriteriaHistoryResponse(@Nullable BigEventCriteriaResponse previous, BigEventCriteriaResponse current,
		ChangeActor actor, Instant changedAt) {

	static BigEventCriteriaHistoryResponse of(BigEventCriteriaChange change) {
		return new BigEventCriteriaHistoryResponse(
				(change.previous() == null) ? null : BigEventCriteriaResponse.of(change.previous()),
				BigEventCriteriaResponse.of(change.current()), change.actor(), change.changedAt());
	}

}
