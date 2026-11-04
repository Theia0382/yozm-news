package net.yozmnews.evaluation;

import org.jspecify.annotations.Nullable;

/**
 * 평가 목록의 거르기 — 태그 하나와 주제 판정. 둘 다 있으면 둘 다 맞아야 한다 · docs/specs/evaluation.md §3 · evaluation-R31.
 *
 * @param tag 거를 태그 — 주제는 대분류 키 · 중분류 전체 키, 지역 키, 형식(대표 형식) 키. 없으면 거르지 않는다
 * @param topicStatus 거를 주제 판정 — 없으면 거르지 않는다
 */
public record EvaluationFilter(@Nullable TagRef tag, @Nullable TopicStatus topicStatus) {

	/** 거르지 않는다 */
	public static final EvaluationFilter NONE = new EvaluationFilter(null, null);

}
