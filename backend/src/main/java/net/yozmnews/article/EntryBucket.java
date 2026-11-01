package net.yozmnews.article;

import java.time.Instant;
import java.util.Objects;

/**
 * 집계 칸 하나의 범위 — 시작 포함, 끝 제외. docs/specs/article.md §3 · article-R13.
 */
public record EntryBucket(Instant from, Instant to) {

	public EntryBucket {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		if (!from.isBefore(to)) {
			throw new IllegalArgumentException("칸의 시작은 끝보다 앞선다: " + from + " ~ " + to);
		}
	}

}
