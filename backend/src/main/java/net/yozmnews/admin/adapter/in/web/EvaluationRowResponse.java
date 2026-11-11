package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.tag.EvaluationRow;
import net.yozmnews.tag.NamedTag;
import net.yozmnews.tag.TagName;
import net.yozmnews.tag.TopicStatus;
import org.jspecify.annotations.Nullable;

/**
 * 목록 · 표본의 평가 한 건 — 판정과 기사 정보 · docs/specs/admin.md §3 · admin-R6. 평가 · 기사 ID 는 JSON 수다.
 *
 * @param article 기사의 제목 · 출처 — 그새 지운 기사면 null
 */
record EvaluationRowResponse(long id, long articleId, Instant evaluatedAt, TopicStatus topicStatus,
		List<NamedTag> topics, List<TagName> topicCategories, List<NamedTag> regions, NamedTag format,
		@Nullable Double importance, @Nullable ArticleSummaryResponse article) {

	static EvaluationRowResponse of(EvaluationRow row, AdminArticles.@Nullable Summary article) {
		return new EvaluationRowResponse(row.id().value(), row.articleId().value(), row.evaluatedAt(),
				row.topicStatus(), row.topics(), row.topicCategories(), row.regions(), row.format(), row.importance(),
				(article == null) ? null : ArticleSummaryResponse.of(article));
	}

}
