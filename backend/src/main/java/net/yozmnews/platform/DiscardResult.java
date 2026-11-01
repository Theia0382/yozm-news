package net.yozmnews.platform;

import java.util.Objects;
import java.util.UUID;

/**
 * 폐기한 발행 하나의 결과 — docs/specs/platform.md §3.
 *
 * @param id 발행 ID
 * @param outcome 결과
 */
public record DiscardResult(UUID id, DiscardOutcome outcome) {

	public DiscardResult {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(outcome, "outcome");
	}

}
