package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.evaluation.JudgementPeriod;
import org.jspecify.annotations.Nullable;

/**
 * 판정 기록 한 쪽과 지금 사건 배정 기준, 날짜 범위의 상한 — docs/specs/admin.md §3 · admin-R7. 상한은 {@link JudgementPeriod#MAX_DAYS}
 * 에서 읽는다(판정 기록의 기간은 판정 시각이다).
 *
 * @param next 다음 쪽 커서 — 더 없으면 null
 */
record EventJudgementListResponse(List<EventJudgementRowResponse> judgements, @Nullable JudgementCursorResponse next,
		AssignmentCriteriaResponse criteria, int maxDays) {

	static EventJudgementListResponse of(List<EventJudgementRowResponse> judgements,
			@Nullable JudgementCursorResponse next, AssignmentCriteriaResponse criteria) {
		return new EventJudgementListResponse(judgements, next, criteria, JudgementPeriod.MAX_DAYS);
	}

}
