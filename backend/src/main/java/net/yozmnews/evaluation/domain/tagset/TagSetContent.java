package net.yozmnews.evaluation.domain.tagset;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import net.yozmnews.evaluation.TagKind;

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

	/**
	 * 저장된 버전 내용 가운데 이 내용과 같은 것이 있는가 — 기본 체계 파일의 개정을 알리는 판정(evaluation-R2). 내용은 버전 내용이고 태그
	 * 기준 · 저장 기준은 들지 않는다.
	 */
	public boolean matchesAny(Collection<TagSetContent> stored) {
		return stored.contains(this);
	}

	/**
	 * 태그 종류의 키 — 체계 순이다. 주제는 대분류 키와 그 중분류 전체 키를 대분류마다 이어 낸다 — 태그 종류별 키 집합의 정본
	 * (evaluation-R22 ~ R26).
	 */
	public List<String> keys(TagKind kind) {
		return switch (kind) {
			case TOPIC -> this.topics.stream()
				.flatMap(topic -> Stream.concat(Stream.of(topic.key().value()),
						topic.subtopics().stream().map(subtopic -> subtopic.key().value())))
				.toList();
			case REGION -> this.regions.stream().map(region -> region.key().value()).toList();
			case FORMAT -> this.formats.stream().map(format -> format.key().value()).toList();
		};
	}

}
