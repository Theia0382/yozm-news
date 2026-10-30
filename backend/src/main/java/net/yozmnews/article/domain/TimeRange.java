package net.yozmnews.article.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 시각 범위 — 양 끝 포함.
 *
 * @param to 끝 — 시작보다 앞서지 않는다
 */
public record TimeRange(Instant from, Instant to) {

	public TimeRange {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		if (to.isBefore(from)) {
			throw new IllegalArgumentException("끝이 시작보다 앞선다: " + from + " ~ " + to);
		}
	}

}
