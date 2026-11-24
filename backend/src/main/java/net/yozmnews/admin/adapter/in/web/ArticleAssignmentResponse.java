package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.evaluation.EventJudgementView;
import net.yozmnews.event.ArticleAssignment;
import net.yozmnews.event.DecidedBy;
import net.yozmnews.event.EventView;
import org.jspecify.annotations.Nullable;

/**
 * 기사 상세의 사건 소속 — 배정과 그 근거, 반영한 사건 소속 판정 원본 · docs/specs/admin.md §5 admin-R8. 사건 · 판정 원본 ID 는 JSON 수다.
 *
 * @param eventId 배정된 사건
 * @param event 그 사건의 지금 값 — 조회 사이에 사건이 사라졌으면 null
 * @param score 붙인 후보의 점수, 새 사건이면 가장 높은 후보의 점수 — 후보가 없었으면 null
 * @param opened 연 사건 표지 — 이 기사가 이 배정의 사건을 열었다
 * @param criteria 배정을 판정 · 재판정한 기준
 * @param candidates 저장 후보 — 점수 내림차순, 같으면 사건 ID 순
 * @param judgementId 반영한 사건 소속 판정 원본 — 반영하지 않았으면 null
 * @param judgement 그 원본 — 원본이 없거나 지웠으면 null
 */
record ArticleAssignmentResponse(long eventId, @Nullable AssignedEventResponse event, Instant publishedAt,
		@Nullable Double score, DecidedBy decidedBy, boolean opened, AssignmentCriteriaResponse criteria,
		Instant assignedAt, List<AssignmentCandidateResponse> candidates, @Nullable Long judgementId,
		@Nullable ArticleJudgementResponse judgement) {

	static ArticleAssignmentResponse of(ArticleAssignment assignment, @Nullable EventView event,
			@Nullable EventJudgementView judgement) {
		return new ArticleAssignmentResponse(assignment.event().value(),
				(event == null) ? null : AssignedEventResponse.of(event), assignment.publishedAt(), assignment.score(),
				assignment.decidedBy(), assignment.opened(), AssignmentCriteriaResponse.of(assignment.criteria()),
				assignment.assignedAt(), assignment.candidates().stream().map(AssignmentCandidateResponse::of).toList(),
				(assignment.judgement() == null) ? null : assignment.judgement().value(),
				(judgement == null) ? null : ArticleJudgementResponse.of(judgement));
	}

}
