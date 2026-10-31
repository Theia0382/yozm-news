package net.yozmnews.source;

import java.util.Objects;

/**
 * 소스 켜기의 결과 — docs/specs/source.md §3
 */
public sealed interface SourceEnableResult {

	/** 켰다 */
	record Enabled() implements SourceEnableResult {
	}

	/** 켜지 않았다 — 아무것도 바꾸지 않았다 */
	record Refused(ActivationRefusal reason) implements SourceEnableResult {

		public Refused {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
