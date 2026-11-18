package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;

import net.yozmnews.digest.DigestUnresolvedFailure;

/**
 * 해결되지 않은 마지막 실패 — 알림 화면은 날짜 · 끝낸 시각이 같은 실패 알림에만 다시 만들기 링크를 단다. docs/specs/reader.md §5 · reader-R9
 *
 * @param digestDate 실패한 다이제스트의 날짜
 * @param failedAt 끝낸 시각 — 그 실패 알림의 시각과 같은 값이다
 */
record DigestUnresolvedFailureResponse(LocalDate digestDate, Instant failedAt) {

	static DigestUnresolvedFailureResponse of(DigestUnresolvedFailure failure) {
		return new DigestUnresolvedFailureResponse(failure.digestDate(), failure.failedAt());
	}

}
