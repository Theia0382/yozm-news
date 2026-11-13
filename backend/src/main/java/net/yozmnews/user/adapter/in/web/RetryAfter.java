package net.yozmnews.user.adapter.in.web;

import java.time.Duration;
import java.time.Instant;

/**
 * 대기 응답의 남은 초 — 올림, 1 이상. 실패 지연을 거는 응답(로그인 · 비밀번호 변경 · 복구 코드)이 함께 쓰는 정본. docs/specs/user.md §3 ·
 * user-R17
 */
final class RetryAfter {

	private RetryAfter() {
	}

	/** 다시 시도할 수 있는 때까지 남은 초 */
	static long seconds(Instant retryAt, Instant now) {
		Duration remaining = Duration.between(now, retryAt);
		long seconds = remaining.toSeconds() + ((remaining.toNanosPart() > 0) ? 1 : 0);
		return Math.max(1, seconds);
	}

}
