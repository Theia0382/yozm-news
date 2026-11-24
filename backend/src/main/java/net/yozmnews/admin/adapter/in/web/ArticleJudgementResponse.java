package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.evaluation.EventJudgementView;

/**
 * 기사 상세의 사건 소속 판정 원본 — 배정이 반영한 판정 · docs/specs/admin.md §5 admin-R8. 원본 응답은 싣지 않는다.
 *
 * @param candidates 물은 후보 — 요청 순서, 판정 당시의 사건 ID
 */
record ArticleJudgementResponse(long id, String evaluator, String modelVersion, String wordingVersion,
		int inputTokens, Instant judgedAt, List<JudgedEventResponse> candidates) {

	static ArticleJudgementResponse of(EventJudgementView view) {
		return new ArticleJudgementResponse(view.id().value(), view.evaluator(), view.modelVersion(),
				view.wordingVersion(), view.inputTokens(), view.judgedAt(),
				view.candidates().stream().map(JudgedEventResponse::of).toList());
	}

}
