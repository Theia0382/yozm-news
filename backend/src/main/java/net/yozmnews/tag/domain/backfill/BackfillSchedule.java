package net.yozmnews.tag.domain.backfill;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 빠진 태그 채움의 일정 — 시작 지연 · 점유 기간 · 묶음 크기의 정본 · docs/specs/tag.md §5.
 * <p>
 * 값은 모두 측정 전 목표다.
 */
public final class BackfillSchedule {

	/**
	 * 요청 뒤 시작할 수 있기까지 — 평가 시각을 찍은 결과가 커밋되기까지의 여유. 결과 트랜잭션은 활성 전환의 표 잠금을 기다릴 수 있다
	 */
	public static final Duration START_DELAY = Duration.ofMinutes(1);

	/** 회차가 채움을 점유하는 기간 — 묶음마다 갱신한다. 만료되면 다른 회차가 커서부터 잇는다 */
	public static final Duration LEASE = Duration.ofMinutes(10);

	/** 묶음 하나의 평가 수 — 묶음마다 트랜잭션 하나 */
	public static final int BATCH = 500;

	private BackfillSchedule() {
	}

	/** 요청 시각에 기록한 채움을 시작할 수 있는 시각 */
	public static Instant eligibleAt(Instant requestedAt) {
		return Objects.requireNonNull(requestedAt, "requestedAt").plus(START_DELAY);
	}

	/** 지금 점유하거나 갱신한 점유의 만료 시각 */
	public static Instant leaseUntil(Instant now) {
		return Objects.requireNonNull(now, "now").plus(LEASE);
	}

}
