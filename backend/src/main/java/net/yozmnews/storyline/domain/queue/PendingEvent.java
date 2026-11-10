package net.yozmnews.storyline.domain.queue;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * 잇기 대기 행 하나와 그 점유 — docs/specs/storyline.md §4 · storyline-R2 · storyline-R3.
 *
 * @param orderAt 순서 키 — 사건의 첫 기사 시각, 지워졌거나 합쳐진 사건이면 null(맨 앞)
 * @param notifications 다시 알림 수 — 처리 중에 다시 온 알림을 가른다
 * @param failures 판정 실패 수
 * @param lease 회차의 점유 — 없으면 null
 */
public record PendingEvent(long eventId, @Nullable Instant orderAt, int notifications, int failures,
		@Nullable LinkingLease lease) {

	/**
	 * @throws IllegalArgumentException 다시 알림 수 · 실패 수가 음수일 때
	 */
	public PendingEvent {
		if (notifications < 0 || failures < 0) {
			throw new IllegalArgumentException("다시 알림 수 · 실패 수는 0 이상이다: " + notifications + " · " + failures);
		}
	}

	/** 회차가 점유한다 — 만료는 {@code now} + {@link LinkingSchedule#LEASE} */
	public PendingEvent claim(UUID token, Instant now) {
		return new PendingEvent(this.eventId, this.orderAt, this.notifications, this.failures,
				new LinkingLease(token, now.plus(LinkingSchedule.LEASE)));
	}

}
