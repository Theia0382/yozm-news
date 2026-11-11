package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * Jev 에 묻는 태그 — 주제 중분류(전체 키) · 지역 — docs/specs/evaluation.md §3 · evaluation-R40.
 *
 * @param question 질문 틀에 설명을 끼운 문장
 */
public record AskedTag(String key, String name, String description, String question) {

	public AskedTag {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
		Objects.requireNonNull(question, "question");
	}

}
