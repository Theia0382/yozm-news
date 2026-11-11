package net.yozmnews.tag.domain.queue;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 회차가 대기 행을 가져갔다는 표시 — docs/specs/tag.md §5
 *
 * @param expiresAt 이 시각이 지나면 다른 회차가 다시 가져갈 수 있다
 */
public record EvaluationLease(UUID token, Instant expiresAt) {

	public EvaluationLease {
		Objects.requireNonNull(token, "token");
		Objects.requireNonNull(expiresAt, "expiresAt");
	}

}
