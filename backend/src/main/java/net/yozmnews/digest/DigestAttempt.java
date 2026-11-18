package net.yozmnews.digest;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.user.DigestFailureCause;
import org.jspecify.annotations.Nullable;

/**
 * 날짜의 가장 최근 시도 — 그 날짜의 ID 가 가장 큰 다이제스트 행. 실패 메시지는 싣지 않는다. docs/specs/digest.md §3 · digest-R38
 *
 * @param digestId 다이제스트 ID
 * @param status 상태
 * @param failure 실패 코드 — {@code FAILED} 일 때만
 * @param cause 실패의 갈래(사용자가 할 일) — {@code FAILED} 일 때만
 * @param completedAt 끝낸 시각 — 끝나기 전이면 null
 */
public record DigestAttempt(long digestId, DigestStatus status, @Nullable DigestFailure failure,
		@Nullable DigestFailureCause cause, @Nullable Instant completedAt) {

	public DigestAttempt {
		Objects.requireNonNull(status, "status");
		if ((status == DigestStatus.FAILED) != (failure != null) || (failure == null) != (cause == null)) {
			throw new IllegalArgumentException("실패 코드 · 갈래는 FAILED 일 때만 있다: " + status);
		}
	}

}
