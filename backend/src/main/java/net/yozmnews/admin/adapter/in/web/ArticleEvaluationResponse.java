package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.tag.EvaluationRow;
import net.yozmnews.tag.NamedTag;
import net.yozmnews.tag.TagName;
import net.yozmnews.tag.TopicStatus;
import net.yozmnews.tag.VersionedEvaluationRow;
import org.jspecify.annotations.Nullable;

/**
 * 기사 상세의 평가 하나 — 버전과 판정 요약 · docs/specs/admin.md §5 admin-R8. 평가 · 버전 ID 는 JSON 수다.
 *
 * @param versionNumber 평가 버전의 번호
 * @param importance 중요도 기대값 — 확률 합이 0 이었으면 null
 */
record ArticleEvaluationResponse(long id, long version, int versionNumber, Instant evaluatedAt,
		TopicStatus topicStatus, List<NamedTag> topics, List<TagName> topicCategories, List<NamedTag> regions,
		NamedTag format, @Nullable Double importance) {

	static ArticleEvaluationResponse of(VersionedEvaluationRow versioned) {
		EvaluationRow row = versioned.evaluation();
		return new ArticleEvaluationResponse(row.id().value(), versioned.version().value(), versioned.versionNumber(),
				row.evaluatedAt(), row.topicStatus(), row.topics(), row.topicCategories(), row.regions(), row.format(),
				row.importance());
	}

}
