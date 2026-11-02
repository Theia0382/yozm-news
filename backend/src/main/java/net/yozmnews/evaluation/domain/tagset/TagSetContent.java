package net.yozmnews.evaluation.domain.tagset;

import java.util.List;
import java.util.Objects;

/**
 * 검증을 통과한 태그 체계 내용 — 버전 하나의 바뀌지 않는 부분 — docs/specs/evaluation.md §4 · evaluation-R4.
 * <p>
 * 목록은 파일에 적힌 순서이고, 중요도는 수준 순이다.
 */
public record TagSetContent(QuestionTemplate topicTemplate, QuestionTemplate regionTemplate, String formatQuestion,
		String importanceQuestion, List<TopicCategoryDef> topics, List<TagDef> regions, List<FormatDef> formats,
		List<ImportanceLevelDef> importance) {

	public TagSetContent {
		Objects.requireNonNull(topicTemplate, "topicTemplate");
		Objects.requireNonNull(regionTemplate, "regionTemplate");
		Objects.requireNonNull(formatQuestion, "formatQuestion");
		Objects.requireNonNull(importanceQuestion, "importanceQuestion");
		topics = List.copyOf(topics);
		regions = List.copyOf(regions);
		formats = List.copyOf(formats);
		importance = List.copyOf(importance);
	}

}
