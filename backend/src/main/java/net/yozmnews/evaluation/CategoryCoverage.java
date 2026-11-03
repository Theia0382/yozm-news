package net.yozmnews.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * 대분류 하나의 보도량 — docs/specs/evaluation.md §3
 *
 * @param articles 하위 중분류 어느 것이든 붙은 기사 수 — 하위의 합이 아니다. 중분류 일부만 골라도 대분류 전체의 수다
 * @param subtopics 고른 중분류 — 체계 순서
 */
public record CategoryCoverage(String key, String name, long articles, List<SubtopicCoverage> subtopics) {

	public CategoryCoverage {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		subtopics = List.copyOf(subtopics);
	}

}
