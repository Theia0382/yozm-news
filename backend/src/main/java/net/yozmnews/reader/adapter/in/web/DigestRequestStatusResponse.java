package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;

import net.yozmnews.digest.DigestRequestStatus;
import org.jspecify.annotations.Nullable;

/**
 * 수동 생성 상태 — 지금 요청하면 무엇이 되는가. docs/specs/reader.md §5 · reader-R11
 *
 * @param today 지금의 사용자 시간대 날짜
 * @param timezone 사용자 시간대 — IANA 이름(예: {@code Asia/Seoul})
 * @param usableModel 쓸 모델이 있는가
 * @param inProgress 다이제스트가 생성을 기다리거나 생성 중인가
 * @param remainingToday 오늘 남은 수동 생성 횟수
 * @param resetsAt 횟수가 다시 차는 시각
 * @param targetDate 지금 요청하면 만들 다이제스트의 날짜
 * @param regenerates 그 날짜를 같은 기간으로 다시 만드는가 — 거짓이면 새로 만든다
 * @param scheduledAt 일정이 오늘 다이제스트를 만들 생성 시각 — 그 시각이 아직 오지 않았을 때만, 아니면 null
 * @param unresolvedFailure 해결되지 않은 마지막 실패 — 없으면 null
 */
record DigestRequestStatusResponse(LocalDate today, String timezone, boolean usableModel, boolean inProgress,
		int remainingToday, Instant resetsAt, LocalDate targetDate, boolean regenerates, @Nullable Instant scheduledAt,
		@Nullable DigestUnresolvedFailureResponse unresolvedFailure) {

	static DigestRequestStatusResponse of(DigestRequestStatus status) {
		return new DigestRequestStatusResponse(status.today(), status.timezone().getId(), status.usableModel(),
				status.inProgress(), status.remainingToday(), status.resetsAt(), status.targetDate(),
				status.regenerates(), status.scheduledAt(),
				(status.unresolvedFailure() == null) ? null
						: DigestUnresolvedFailureResponse.of(status.unresolvedFailure()));
	}

}
