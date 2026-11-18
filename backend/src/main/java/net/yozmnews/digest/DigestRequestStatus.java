package net.yozmnews.digest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 수동 생성 상태 — 지금 요청하면 무엇이 되는가. 잠그지 않고 읽은 그 순간의 값이다. docs/specs/digest.md §3 · digest-R40
 *
 * @param today 지금의 사용자 시간대 날짜
 * @param timezone 사용자 시간대 — 일정의 기준 시간대
 * @param usableModel 쓸 모델이 있는가 — 없으면 요청은 {@link DigestRequestOutcome.NoUsableModel}
 * @param inProgress 그 사용자의 다이제스트가 생성을 기다리거나 생성 중인가
 * @param remainingToday 오늘(사용자 시간대) 남은 수동 생성 횟수 — 0 이상
 * @param resetsAt 횟수가 다시 차는 시각 — 사용자 시간대의 다음 날 0시
 * @param targetDate 지금 요청하면 만들 다이제스트의 날짜
 * @param regenerates 그 날짜의 다이제스트를 같은 기간으로 다시 만드는가 — 거짓이면 새 기간
 * @param scheduledAt 일정이 오늘 다이제스트를 만들 생성 시각 — 그 시각이 아직 오지 않았을 때만, 아니면 null
 * @param unresolvedFailure 해결되지 않은 마지막 실패 — 없으면 null
 */
public record DigestRequestStatus(LocalDate today, ZoneId timezone, boolean usableModel, boolean inProgress,
		int remainingToday, Instant resetsAt, LocalDate targetDate, boolean regenerates, @Nullable Instant scheduledAt,
		@Nullable DigestUnresolvedFailure unresolvedFailure) {

	public DigestRequestStatus {
		Objects.requireNonNull(today, "today");
		Objects.requireNonNull(timezone, "timezone");
		Objects.requireNonNull(resetsAt, "resetsAt");
		Objects.requireNonNull(targetDate, "targetDate");
		if (remainingToday < 0) {
			throw new IllegalArgumentException("남은 횟수가 음수다: " + remainingToday);
		}
	}

}
