package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 형식 Choice 의 선택지 — docs/specs/evaluation.md §3
 *
 * @param excludedByDefault 기본 제외 형식인가 — 계정을 만들 때와 그 형식이 새로 생긴 버전을 켤 때 사용자의 제외로 넣는다(docs/specs/user.md
 *     user-R43 · R47)
 */
public record FormatOption(String key, String name, String description, boolean excludedByDefault) {

	public FormatOption {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
	}

}
