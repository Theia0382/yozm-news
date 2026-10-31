package net.yozmnews.source;

import java.util.Objects;

/**
 * 없는 소스를 가리켰다 — docs/specs/source.md §3.
 */
public final class UnknownSourceException extends RuntimeException {

	private final SourceId id;

	public UnknownSourceException(SourceId id) {
		super("없는 소스: " + Objects.requireNonNull(id, "id").value());
		this.id = id;
	}

	public SourceId id() {
		return this.id;
	}

}
