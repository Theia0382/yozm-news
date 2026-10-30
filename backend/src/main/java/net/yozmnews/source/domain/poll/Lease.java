package net.yozmnews.source.domain.poll;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 수집 점유 — source-R15 · source-R16.
 *
 * @param token 점유 토큰 — 그 회차 수집 작업의 JobRunr 작업 id 이기도 하다
 * @param scheduledAt 수집 작업을 예약한 시각 — 같은 소스의 수집 사이 간격은 이 시각으로 띄운다
 */
public record Lease(UUID token, Instant scheduledAt) {

	public Lease {
		Objects.requireNonNull(token, "token");
		Objects.requireNonNull(scheduledAt, "scheduledAt");
	}

}
