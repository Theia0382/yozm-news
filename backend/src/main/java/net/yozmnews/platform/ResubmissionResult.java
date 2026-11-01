package net.yozmnews.platform;

import java.util.Objects;
import java.util.UUID;

/**
 * 다시 처리한 발행 하나의 결과 — docs/specs/platform.md §3.
 *
 * @param id 발행 ID
 * @param outcome 결과
 */
public record ResubmissionResult(UUID id, ResubmissionOutcome outcome) {

	public ResubmissionResult {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(outcome, "outcome");
	}

}
