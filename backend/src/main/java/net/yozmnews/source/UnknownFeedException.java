package net.yozmnews.source;

import java.util.Objects;

/**
 * 없는 피드를 가리켰다 — docs/specs/source.md §3.
 */
public final class UnknownFeedException extends RuntimeException {

	private final FeedId id;

	public UnknownFeedException(FeedId id) {
		super("없는 피드: " + Objects.requireNonNull(id, "id").value());
		this.id = id;
	}

	public FeedId id() {
		return this.id;
	}

}
