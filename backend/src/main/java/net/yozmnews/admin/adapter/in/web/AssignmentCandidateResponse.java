package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.event.AssignmentCandidate;
import org.jspecify.annotations.Nullable;

/**
 * 기사 상세의 저장 후보 하나 · docs/specs/admin.md §5 admin-R8. 사건 ID 는 JSON 수다.
 *
 * @param eventId 배정 때의 후보 사건
 * @param currentEventId 그 사건의 지금 사건 — 합쳐졌으면 남는 사건, 아니면 {@code eventId}
 * @param probability 사건 소속 판정 확률 — 묻지 않았으면 null
 */
record AssignmentCandidateResponse(long eventId, long currentEventId, double score, @Nullable Double probability) {

	static AssignmentCandidateResponse of(AssignmentCandidate candidate) {
		return new AssignmentCandidateResponse(candidate.event().value(), candidate.current().value(),
				candidate.score(), candidate.probability());
	}

}
