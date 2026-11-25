package net.yozmnews.user;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import net.yozmnews.platform.UserId;

/**
 * 변화 리포트가 실패로 끝났다 — 받는 쪽인 이 모듈이 선언하고 {@code report} 가 실패를 쓰는 트랜잭션에서 낸다. 이 모듈은 받아 그 사용자에게 알림을
 * 남긴다. docs/specs/user.md §3
 * <p>
 * 받는 쪽이 선언한다 — {@code report} 는 이 모듈의 공개 조회를 읽어 이 모듈에 기대므로, {@code report} 가 선언한 이벤트를 이 모듈이 받으면 두
 * 모듈이 서로 기댄다({@link DigestFailed} 와 같은 까닭).
 *
 * @param user 리포트의 사용자
 * @param reportId 실패한 리포트(1 이상) — 다시 만든 리포트가 또 실패하면 같은 ID 에 실패 시각이 다르다
 * @param kind 종류
 * @param firstDay 기간 첫날 — 리포트 행의 시간대로 적은 날짜
 * @param lastDay 기간 마지막 날 — 리포트 행의 시간대로 적은 날짜
 * @param cause 실패의 갈래 — 실패 코드를 갈래로 옮기는 정본은 {@code report} 에 있다
 * @param failedAt 실패로 끝낸 시각
 */
public record ReportFailed(UserId user, long reportId, ReportFailedKind kind, LocalDate firstDay, LocalDate lastDay,
		GenerationFailureCause cause, Instant failedAt) {

	/**
	 * @throws IllegalArgumentException 리포트 ID 가 1 보다 작거나 마지막 날이 첫날보다 앞일 때
	 */
	public ReportFailed {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(firstDay, "firstDay");
		Objects.requireNonNull(lastDay, "lastDay");
		Objects.requireNonNull(cause, "cause");
		Objects.requireNonNull(failedAt, "failedAt");
		if (reportId < 1) {
			throw new IllegalArgumentException("리포트 ID 는 1 이상이다: " + reportId);
		}
		if (lastDay.isBefore(firstDay)) {
			throw new IllegalArgumentException("마지막 날이 첫날보다 앞이다: " + firstDay + " ~ " + lastDay);
		}
	}

}
