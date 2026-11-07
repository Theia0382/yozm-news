package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.evaluation.EventJudgementView;
import net.yozmnews.event.DecidedBy;
import net.yozmnews.event.JudgementUse;
import org.jspecify.annotations.Nullable;

/**
 * 판정 기록 한 줄 — 판정 원본과 그 쓰임, 판정할 기사의 정보 · docs/specs/admin.md §5 admin-R7.
 * <p>
 * 쓰임은 판별자 {@code use} 하나의 평평한 행이다 — {@code ASSIGNMENT} 면 {@code assignedEventId} · {@code decidedBy},
 * {@code MERGE} 면 {@code mergePairs}, {@code UNUSED}(가리키는 기록이 없다)면 셋 다 비어 있다. 결과는 조회 시점의 값이다.
 *
 * @param article 판정할 기사의 제목 · 출처 — 그새 지웠으면 null
 * @param candidates 물은 후보 — 요청 순서, 사건 ID 는 판정 당시의 값
 * @param inputTokens 판정 요청의 입력 토큰
 */
record EventJudgementRowResponse(long id, Instant judgedAt, long articleId, @Nullable ArticleSummaryResponse article,
		List<JudgedEventResponse> candidates, int inputTokens, String modelVersion, Use use,
		@Nullable Long assignedEventId, @Nullable DecidedBy decidedBy, List<MergePairResponse> mergePairs) {

	static EventJudgementRowResponse of(EventJudgementView view, @Nullable JudgementUse use,
			AdminArticles.@Nullable Summary article) {
		List<JudgedEventResponse> candidates = view.candidates().stream().map(JudgedEventResponse::of).toList();
		ArticleSummaryResponse summary = (article == null) ? null : ArticleSummaryResponse.of(article);
		long id = view.id().value();
		long articleId = view.articleId().value();
		if (use == null) {
			return new EventJudgementRowResponse(id, view.judgedAt(), articleId, summary, candidates,
					view.inputTokens(), view.modelVersion(), Use.UNUSED, null, null, List.of());
		}
		return switch (use) {
			case JudgementUse.Assignment assignment -> new EventJudgementRowResponse(id, view.judgedAt(), articleId,
					summary, candidates, view.inputTokens(), view.modelVersion(), Use.ASSIGNMENT,
					assignment.event().value(), assignment.decidedBy(), List.of());
			case JudgementUse.Merge merge -> new EventJudgementRowResponse(id, view.judgedAt(), articleId, summary,
					candidates, view.inputTokens(), view.modelVersion(), Use.MERGE, null, null,
					merge.pairs().stream().map(MergePairResponse::of).toList());
		};
	}

	enum Use {

		/** 이 판정을 반영해 배정을 썼다 — 그 기사의 지금 배정 */
		ASSIGNMENT,
		/** 이 판정으로 병합 판정 기록을 남겼다 — 쌍마다 지금 합쳐졌는지 */
		MERGE,
		/** 이 판정을 가리키는 기록이 없다 — 쓰기를 되돌렸거나, 쌍의 기록이 이미 있었거나, 기록이 함께 지워졌거나, 기록을 남기기 전의 판정 */
		UNUSED

	}

}
