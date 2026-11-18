package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;

import net.yozmnews.digest.DigestAttempt;
import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.DigestStatus;
import net.yozmnews.user.DigestFailureCause;
import org.jspecify.annotations.Nullable;

/**
 * 날짜의 가장 최근 시도 — 상태와 실패의 코드 · 갈래. 실패 메시지는 싣지 않는다. docs/specs/reader.md §5 · reader-R9
 *
 * @param digestId 다이제스트 ID
 * @param status 상태
 * @param failure 실패 코드 — 실패일 때만
 * @param cause 실패의 갈래(사용자가 할 일) — 실패일 때만
 * @param completedAt 끝낸 시각 — 끝나기 전이면 null
 */
record DigestAttemptResponse(long digestId, DigestStatus status, @Nullable DigestFailure failure,
		@Nullable DigestFailureCause cause, @Nullable Instant completedAt) {

	static DigestAttemptResponse of(DigestAttempt attempt) {
		return new DigestAttemptResponse(attempt.digestId(), attempt.status(), attempt.failure(), attempt.cause(),
				attempt.completedAt());
	}

}
