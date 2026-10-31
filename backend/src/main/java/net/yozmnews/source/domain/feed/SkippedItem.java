package net.yozmnews.source.domain.feed;

import java.util.Objects;

import net.yozmnews.source.SkipReason;

/**
 * 기사로 넘기지 않은 피드 항목.
 *
 * @param position 문서 안 항목 순번(1부터)
 */
public record SkippedItem(int position, SkipReason reason) {

	public SkippedItem {
		if (position < 1) {
			throw new IllegalArgumentException("항목 순번은 1부터다: " + position);
		}
		Objects.requireNonNull(reason, "reason");
	}

}
