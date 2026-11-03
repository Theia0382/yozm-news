package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 중분류 하나의 보도량 — docs/specs/evaluation.md §3
 *
 * @param key 전체 키
 * @param articles 그 중분류가 붙은 기사 수
 */
public record SubtopicCoverage(String key, String name, long articles) {

	public SubtopicCoverage {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
	}

}
