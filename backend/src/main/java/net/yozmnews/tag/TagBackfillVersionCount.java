package net.yozmnews.tag;

import java.util.Objects;

/**
 * 채울 범위의 버전 하나의 평가 수 — docs/specs/tag.md §3
 *
 * @param number 버전 번호
 */
public record TagBackfillVersionCount(TagSetId version, int number, long evaluations) {

	public TagBackfillVersionCount {
		Objects.requireNonNull(version, "version");
	}

}
