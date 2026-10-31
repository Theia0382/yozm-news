package net.yozmnews.source;

import java.util.Objects;

/**
 * 기사로 넘기지 않은 피드 항목 — 문서 순서의 순번(1부터)과 이유 — docs/specs/source.md §3
 */
public record SkippedItemView(int position, SkipReason reason) {

	public SkippedItemView {
		Objects.requireNonNull(reason, "reason");
	}

}
