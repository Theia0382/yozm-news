package net.yozmnews.tag;

import java.util.LinkedHashSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 평가 목록의 거르기 — 태그 여럿과 주제 판정. 고른 것이 모두 맞아야 한다 · docs/specs/tag.md §3 · tag-R33.
 *
 * @param tags 거를 태그 — 주제는 대분류 키 · 중분류 전체 키, 지역 키, 형식(대표 형식) 키. 고른 순서이고 같은 태그는 처음 자리 하나만 남는다.
 * 비면 태그로 거르지 않는다
 * @param topicStatus 거를 주제 판정 — 없으면 거르지 않는다
 */
public record EvaluationFilter(List<TagRef> tags, @Nullable TopicStatus topicStatus) {

	/** 거르지 않는다 */
	public static final EvaluationFilter NONE = new EvaluationFilter(List.of(), null);

	public EvaluationFilter {
		tags = List.copyOf(new LinkedHashSet<>(tags));
	}

}
