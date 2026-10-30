package net.yozmnews.source;

/**
 * 소스(언론사) 식별자 — docs/specs/source.md §3
 */
public record SourceId(long value) {

	public SourceId {
		if (value < 1) {
			throw new IllegalArgumentException("소스 식별자는 1 이상이어야 한다: " + value);
		}
	}

}
