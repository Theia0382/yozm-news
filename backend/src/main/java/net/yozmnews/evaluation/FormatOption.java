package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 형식 Choice 의 선택지 — docs/specs/evaluation.md §3
 *
 * @param excludedByDefault 사용자가 따로 정하지 않으면 후보에서 빼는 형식인가
 */
public record FormatOption(String key, String name, String description, boolean excludedByDefault) {

	public FormatOption {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
	}

}
