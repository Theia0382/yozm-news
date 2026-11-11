package net.yozmnews.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * 태그 판정의 질문 묶음 — 주제 대분류와 그 중분류 · 지역 · 형식 선택지 · 중요도 수준과 각 질문 문장 · docs/specs/evaluation.md §3 ·
 * evaluation-R40.
 * <p>
 * 넘긴 질문 문장으로 묻는다 — 관문은 문장을 만들지 않는다. 검증된 체계에서 온 값이다 — 구성 규칙은 {@code tag} 가 검증하고(tag-R7), 이
 * 타입은 구성을 다시 검사하지 않는다.
 */
public record TagQuestions(List<TopicCategory> topics, List<AskedTag> regions, FormatChoice format,
		ImportanceScale importance) {

	public TagQuestions {
		topics = List.copyOf(topics);
		regions = List.copyOf(regions);
		Objects.requireNonNull(format, "format");
		Objects.requireNonNull(importance, "importance");
	}

}
