package net.yozmnews.tag;

import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import org.jspecify.annotations.Nullable;

/**
 * 기사 하나의 평가 — 조회 시점의 판정 · docs/specs/tag.md §3.
 *
 * @param tagSetId 평가한 태그 체계 버전
 * @param topics 붙은 주제 중분류(전체 키)와 확률 — 체계 순서. {@link TopicStatus#LOW_CONFIDENCE} 면 하나,
 * {@link TopicStatus#UNCLASSIFIED} 면 빈 목록
 * @param topicCategories 붙은 중분류의 대분류 키 — 체계 순서, 중복 없음
 * @param regions 붙은 지역과 확률 — 체계 순서
 * @param format 대표 형식 키
 * @param importance 중요도 기대값 — 없으면 null
 */
public record ArticleEvaluationView(ArticleId articleId, TagSetId tagSetId, TopicStatus topicStatus,
		List<TagProbability> topics, List<String> topicCategories, List<TagProbability> regions, String format,
		@Nullable Double importance) {

	public ArticleEvaluationView {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(tagSetId, "tagSetId");
		Objects.requireNonNull(topicStatus, "topicStatus");
		topics = List.copyOf(topics);
		topicCategories = List.copyOf(topicCategories);
		regions = List.copyOf(regions);
		Objects.requireNonNull(format, "format");
	}

}
