package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.tag.DistributionShare;
import net.yozmnews.tag.EvaluationDetailView;
import net.yozmnews.tag.StoredTagView;
import net.yozmnews.tag.TagName;
import net.yozmnews.tag.TagThresholds;
import net.yozmnews.tag.TopicStatus;
import org.jspecify.annotations.Nullable;

/**
 * 평가 하나의 상세와 기사 정보 — docs/specs/admin.md §3 · admin-R6. 평가 · 기사 · 버전 ID 는 JSON 수다.
 *
 * @param topicThresholds 평가 버전의 지금 주제 태그 기준 · 저장 기준
 * @param regionThresholds 평가 버전의 지금 지역 태그 기준 · 저장 기준
 * @param article 기사의 제목 · 요약문 · 원문 링크 · 엔트리 — 그새 지운 기사면 null
 */
record EvaluationDetailResponse(long id, long articleId, long version, int versionNumber, String evaluator,
		String modelVersion, Instant evaluatedAt, int inputTokens, TagThresholds topicThresholds,
		TagThresholds regionThresholds, TopicStatus topicStatus, List<StoredTagView> topics,
		List<TagName> topicCategories, List<StoredTagView> regions, List<DistributionShare> formats, String format,
		List<DistributionShare> importanceLevels, @Nullable Double importance,
		@Nullable ArticleDetailResponse article) {

	static EvaluationDetailResponse of(EvaluationDetailView detail, AdminArticles.@Nullable Detail article) {
		return new EvaluationDetailResponse(detail.id().value(), detail.articleId().value(), detail.version().value(),
				detail.versionNumber(), detail.evaluator(), detail.modelVersion(), detail.evaluatedAt(),
				detail.inputTokens(), detail.thresholds().topic(), detail.thresholds().region(), detail.topicStatus(),
				detail.topics(), detail.topicCategories(), detail.regions(), detail.formats(), detail.format(),
				detail.importanceLevels(), detail.importance(),
				(article == null) ? null : ArticleDetailResponse.of(article));
	}

}
