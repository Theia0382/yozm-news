package net.yozmnews.tag;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import org.jspecify.annotations.Nullable;

/**
 * 평가 한 건의 판정 — 그 평가 버전의 지금 태그 기준 · 저장 기준으로 조회 때 판정한다 · docs/specs/tag.md §3 · tag-R33.
 *
 * @param topics 붙은 주제 중분류 — 체계 순
 * @param topicCategories 붙은 중분류의 대분류 — 체계 순
 * @param regions 붙은 지역 — 체계 순
 * @param format 대표 형식과 그 확률
 * @param importance 중요도 기대값 — 확률 합이 0 이었으면 null
 */
public record EvaluationRow(EvaluationId id, ArticleId articleId, Instant evaluatedAt, TopicStatus topicStatus,
		List<NamedTag> topics, List<TagName> topicCategories, List<NamedTag> regions, NamedTag format,
		@Nullable Double importance) {

	public EvaluationRow {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(evaluatedAt, "evaluatedAt");
		Objects.requireNonNull(topicStatus, "topicStatus");
		topics = List.copyOf(topics);
		topicCategories = List.copyOf(topicCategories);
		regions = List.copyOf(regions);
		Objects.requireNonNull(format, "format");
	}

}
