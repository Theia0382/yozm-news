package net.yozmnews.storyline.domain.relation;

import java.time.Duration;
import java.time.Instant;

/**
 * 관계 대기의 일정 — 점유 기간 · 회차의 점유 상한 · 스토리라인마다 쌍 수 · 일시 실패 뒤 판정 가능 시각의 정본 ·
 * docs/specs/storyline.md §5 · storyline-R31.
 * <p>
 * 값은 모두 측정 전 목표다.
 */
public final class RelationSchedule {

	/** 회차가 대기 행을 점유하는 기간 — 만료되면 다른 회차가 다시 가져간다 */
	public static final Duration LEASE = Duration.ofMinutes(20);

	/** 회차 하나가 점유하는 대기 행 수의 상한 */
	public static final int BATCH_SIZE = 4;

	/** 회차 하나가 스토리라인 하나에서 묻는 쌍 수의 상한 */
	public static final int MAX_PAIRS = 5;

	/** 평가기 일시 실패 뒤 미루기의 첫 간격 — 실패마다 두 배 */
	private static final Duration FIRST_RETRY = Duration.ofMinutes(1);

	/** 미루기 간격의 상한 — 일시 실패가 이어지는 동안 스토리라인마다 이 간격으로 다시 묻는다 */
	private static final Duration MAX_RETRY = Duration.ofHours(1);

	private RelationSchedule() {
	}

	/**
	 * 평가기 일시 실패 뒤 판정 가능 시각 — 지금 + 1분 × 2^(실패 수 − 1), 간격은 1시간에서 멈춘다. 실패 수에는 상한이 없다 — 간격이 1시간에
	 * 이르면 더 곱하지 않아 실패 수가 커도 넘치지 않는다.
	 * @param failures 이번 실패를 센 뒤의 실패 수 — 1 이상
	 * @throws IllegalArgumentException 실패 수가 1 미만일 때
	 */
	public static Instant retryAt(int failures, Instant now) {
		if (failures < 1) {
			throw new IllegalArgumentException("실패 수는 1 이상이다: " + failures);
		}
		Duration delay = FIRST_RETRY;
		for (int i = 1; i < failures && delay.compareTo(MAX_RETRY) < 0; i++) {
			delay = delay.multipliedBy(2);
		}
		return now.plus((delay.compareTo(MAX_RETRY) > 0) ? MAX_RETRY : delay);
	}

}
