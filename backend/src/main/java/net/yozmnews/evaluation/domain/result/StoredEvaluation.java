package net.yozmnews.evaluation.domain.result;

import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagSetId;
import org.jspecify.annotations.Nullable;

/**
 * 저장된 평가 하나 — 조회 시점 판정의 입력 · docs/specs/evaluation.md §4
 *
 * @param importance 중요도 기대값 — 없으면 null
 * @param tags 저장 기준 이상이었던 주제 · 지역 태그
 */
public record StoredEvaluation(ArticleId articleId, TagSetId tagSetId, String formatKey, @Nullable Double importance,
		List<StoredTag> tags) {

	public StoredEvaluation {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(tagSetId, "tagSetId");
		Objects.requireNonNull(formatKey, "formatKey");
		tags = List.copyOf(tags);
	}

}
