package net.yozmnews.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventJudgementId;
import org.jspecify.annotations.Nullable;

/**
 * 기사 하나의 배정과 그 근거 — 배정 행 · 판정에 쓴 기준 · 저장 후보 · docs/specs/event.md §3 · event-R12 · event-R22.
 *
 * @param event 배정된 사건 — 합쳐진 사건의 배정은 남는 사건으로 옮겨져 있다(event-R13)
 * @param score 붙인 후보의 점수, 새 사건이면 가장 높은 후보의 점수 — 후보가 없었으면 null
 * @param opened 연 사건 표지 — 판정으로 이 배정의 사건을 열었다(event-R18)
 * @param criteria 배정을 판정 · 재판정한 기준 — 기준 이력의 값
 * @param judgement 이 배정을 쓸 때 반영한 사건 소속 판정 원본 — 반영하지 않았으면 null
 * @param candidates 저장 후보 — 점수 내림차순, 같으면 사건 ID 순
 */
public record ArticleAssignment(ArticleId articleId, EventId event, Instant publishedAt, @Nullable Double score,
		DecidedBy decidedBy, boolean opened, AssignmentCriteria criteria, Instant assignedAt,
		@Nullable EventJudgementId judgement, List<AssignmentCandidate> candidates) {

	public ArticleAssignment {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(publishedAt, "publishedAt");
		Objects.requireNonNull(decidedBy, "decidedBy");
		Objects.requireNonNull(criteria, "criteria");
		Objects.requireNonNull(assignedAt, "assignedAt");
		candidates = List.copyOf(candidates);
	}

}
