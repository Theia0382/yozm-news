package net.yozmnews.storyline.domain.relation;

import java.time.Instant;
import java.util.UUID;

import net.yozmnews.storyline.domain.queue.LinkingLease;
import org.jspecify.annotations.Nullable;

/**
 * 관계 대기 행 하나와 그 점유 — docs/specs/storyline.md §4 · storyline-R26 · storyline-R31.
 *
 * @param notifications 다시 알림 수 — 처리 중에 다시 들어온 것을 가른다
 * @param failures 판정 실패 수
 * @param lease 회차의 점유 — 없으면 null
 */
public record PendingStoryline(long storylineId, int notifications, int failures, @Nullable LinkingLease lease) {

	/**
	 * @throws IllegalArgumentException 다시 알림 수 · 실패 수가 음수일 때
	 */
	public PendingStoryline {
		if (notifications < 0 || failures < 0) {
			throw new IllegalArgumentException("다시 알림 수 · 실패 수는 0 이상이다: " + notifications + " · " + failures);
		}
	}

	/** 회차가 점유한다 — 만료는 {@code now} + {@link RelationSchedule#LEASE} */
	public PendingStoryline claim(UUID token, Instant now) {
		return new PendingStoryline(this.storylineId, this.notifications, this.failures,
				new LinkingLease(token, now.plus(RelationSchedule.LEASE)));
	}

}
