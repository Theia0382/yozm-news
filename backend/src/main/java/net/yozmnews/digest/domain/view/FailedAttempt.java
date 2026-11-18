package net.yozmnews.digest.domain.view;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 실패로 끝난 다이제스트 행 하나 — 해결되지 않은 마지막 실패를 정하는 재료. docs/specs/digest.md §4 · digest-R40
 *
 * @param id 다이제스트 ID
 * @param digestDate 다이제스트 날짜
 * @param completedAt 끝낸 시각 — 실패 알림의 시각과 같은 값이다
 */
public record FailedAttempt(long id, LocalDate digestDate, Instant completedAt) {

	public FailedAttempt {
		Objects.requireNonNull(digestDate, "digestDate");
		Objects.requireNonNull(completedAt, "completedAt");
	}

}
