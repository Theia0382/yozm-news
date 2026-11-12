package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.platform.ChangeActor;
import net.yozmnews.storyline.BigStorylineCriteriaChange;
import org.jspecify.annotations.Nullable;

/**
 * 큰 판정 기준 이력의 한 행 — docs/specs/admin.md §3 · storyline-R20.
 *
 * @param previous 바꾸기 전 값 — 처음 값이면 null
 */
record BigStorylineCriteriaHistoryResponse(@Nullable BigStorylineCriteriaResponse previous, BigStorylineCriteriaResponse current,
		ChangeActor actor, Instant changedAt) {

	static BigStorylineCriteriaHistoryResponse of(BigStorylineCriteriaChange change) {
		return new BigStorylineCriteriaHistoryResponse(
				(change.previous() == null) ? null : BigStorylineCriteriaResponse.of(change.previous()),
				BigStorylineCriteriaResponse.of(change.current()), change.actor(), change.changedAt());
	}

}
