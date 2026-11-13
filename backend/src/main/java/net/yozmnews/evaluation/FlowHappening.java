package net.yozmnews.evaluation;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 흐름의 서술 사건 하나 — 그 사건의 대표 기사와 시각 · docs/specs/evaluation.md §3 · evaluation-R48.
 *
 * @param articleId 사건의 대표 기사 — 제목(과 요약문)을 읽는다
 * @param at 사건의 시각 — 요청에 날짜로 싣는다
 * @param summarized 요약문을 싣는가 — 요약문이 없는 기사면 싣지 않는다
 */
public record FlowHappening(ArticleId articleId, Instant at, boolean summarized) {

	public FlowHappening {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(at, "at");
	}

}
