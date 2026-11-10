package net.yozmnews.storyline.domain.queue;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 회차가 대기 행을 가져갔다는 표시 — docs/specs/storyline.md §4 · storyline-R3.
 *
 * @param expiresAt 만료 시각 — 이 시각부터(이 시각 포함) 다른 회차가 다시 가져갈 수 있다
 */
public record LinkingLease(UUID token, Instant expiresAt) {

	public LinkingLease {
		Objects.requireNonNull(token, "token");
		Objects.requireNonNull(expiresAt, "expiresAt");
	}

}
