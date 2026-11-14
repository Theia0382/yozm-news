package net.yozmnews.user.domain.throttle;

import java.time.Duration;
import java.time.Instant;

/**
 * 다시 시도할 수 있을 때까지 남은 초 — 올림, 1 이상. 실패 지연을 거는 응답(로그인 · 비밀번호 변경 · 복구 코드)과 요청 제한 거부의
 * {@code Retry-After} 가 함께 쓰는 정본. docs/specs/user.md §3 · user-R17 · user-R40
 */
public final class RetryAfter {

	private RetryAfter() {
	}

	/** 다시 시도할 수 있는 때까지 남은 초 */
	public static long seconds(Instant retryAt, Instant now) {
		Duration remaining = Duration.between(now, retryAt);
		long seconds = remaining.toSeconds() + ((remaining.toNanosPart() > 0) ? 1 : 0);
		return Math.max(1, seconds);
	}

}
