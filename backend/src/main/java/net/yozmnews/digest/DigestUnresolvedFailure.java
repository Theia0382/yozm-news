package net.yozmnews.digest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 해결되지 않은 마지막 실패 — 그 사용자의 가장 최근 실패 뒤에 기간을 덮는 다이제스트가 없다. docs/specs/digest.md §3 · digest-R40
 *
 * @param digestDate 실패한 다이제스트의 날짜
 * @param failedAt 끝낸 시각 — 그 실패의 알림(user 의 {@code DigestFailed})이 가진 시각과 같은 값이다
 */
public record DigestUnresolvedFailure(LocalDate digestDate, Instant failedAt) {

	public DigestUnresolvedFailure {
		Objects.requireNonNull(digestDate, "digestDate");
		Objects.requireNonNull(failedAt, "failedAt");
	}

}
