package net.yozmnews.user.domain.throttle;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * 한 이메일의 연속 로그인 실패 — 횟수와 마지막 실패 시각. 0 회면 시각이 없다. docs/specs/user.md §4 · user-R19
 *
 * @param failures 연속 실패 횟수
 * @param lastFailedAt 마지막 실패 시각 — 0 회면 null
 */
public record FailureRecord(int failures, @Nullable Instant lastFailedAt) {

	/** 실패가 없다 */
	public static final FailureRecord NONE = new FailureRecord(0, null);

	public FailureRecord {
		if (failures < 0) {
			throw new IllegalArgumentException("실패 횟수는 0 이상이어야 한다: " + failures);
		}
		if ((failures == 0) != (lastFailedAt == null)) {
			throw new IllegalArgumentException("실패가 없을 때만 마지막 실패 시각이 없다");
		}
	}

}
