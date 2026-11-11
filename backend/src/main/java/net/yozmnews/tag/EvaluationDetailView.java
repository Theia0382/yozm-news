package net.yozmnews.tag;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import org.jspecify.annotations.Nullable;

/**
 * 평가 하나의 상세 — 저장 행 전부와 그 버전의 지금 기준으로 붙었는지, 분포 · 모델 · 토큰 · docs/specs/tag.md §3.
 *
 * @param versionNumber 평가 버전의 번호
 * @param thresholds 평가 버전의 지금 태그 기준 · 저장 기준
 * @param topics 저장된 주제 중분류 전부 — 체계 순
 * @param topicCategories 붙은 중분류의 대분류 — 체계 순
 * @param regions 저장된 지역 전부 — 체계 순
 * @param formats 형식 분포 — 체계 순
 * @param format 대표 형식의 키
 * @param importanceLevels 중요도 분포 — 수준 순
 * @param importance 중요도 기대값 — 확률 합이 0 이었으면 null
 */
public record EvaluationDetailView(EvaluationId id, ArticleId articleId, TagSetId version, int versionNumber,
		String evaluator, String modelVersion, Instant evaluatedAt, int inputTokens, TagSetThresholds thresholds,
		TopicStatus topicStatus, List<StoredTagView> topics, List<TagName> topicCategories,
		List<StoredTagView> regions, List<DistributionShare> formats, String format,
		List<DistributionShare> importanceLevels, @Nullable Double importance) {

	public EvaluationDetailView {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(evaluatedAt, "evaluatedAt");
		Objects.requireNonNull(thresholds, "thresholds");
		Objects.requireNonNull(topicStatus, "topicStatus");
		topics = List.copyOf(topics);
		topicCategories = List.copyOf(topicCategories);
		regions = List.copyOf(regions);
		formats = List.copyOf(formats);
		Objects.requireNonNull(format, "format");
		importanceLevels = List.copyOf(importanceLevels);
	}

}
