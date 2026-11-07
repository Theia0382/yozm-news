package net.yozmnews.event.domain.assignment;

import java.time.Duration;
import java.time.Instant;

/**
 * 배정 대기 · 재판정 회차의 상수 — 점유 기간 · 회차 크기 · 재시도 간격과 상한 · 서술 기사 수 · 닫힌 사건 일괄 유지 수의 정본 · docs/specs/event.md §4 · event-R16 · event-R18.
 * <p>
 * 값은 모두 측정 전 목표다. 점유 기간은 회차 한 묶음이 기사마다 판정 요청 둘을 마감(15초)까지 기다려도 안에 들게 잡는다 — 32 × 2 × 15초 =
 * 16분 &lt; 20분.
 */
public final class AssignmentSchedule {

	/** 배정 회차 하나가 점유하는 대기 행 수의 상한 */
	public static final int BATCH_SIZE = 32;

	/** 배정 회차가 대기 행을 점유하는 기간 — 만료되면 다른 회차가 다시 가져간다 */
	public static final Duration LEASE = Duration.ofMinutes(20);

	/** 일시 실패의 시도 상한 — 이 번째 일시 실패에서 판정 실패로 끝낸다 */
	public static final int MAX_ATTEMPTS = 5;

	/** 첫 일시 실패 뒤의 재시도 간격 — 실패마다 두 배 */
	public static final Duration FIRST_RETRY = Duration.ofMinutes(1);

	/** 재시도 간격의 상한 */
	public static final Duration MAX_RETRY = Duration.ofHours(1);

	/** 사건 소속 판정에 후보 사건마다 넘기는 서술 기사 수 — 사건 소속 판정의 상한(5) 이하 */
	public static final int DESCRIBED_ARTICLES = 3;

	/** 재판정 회차 하나가 다시 판정하는 배정 수의 상한 */
	public static final int REJUDGE_BATCH = 200;

	/** 재판정 회차 하나가 닫힌 사건의 배정을 기준만 새것으로 적는 수의 상한 — 하나씩 다시 판정하는 대상 앞을 막지 않게 한꺼번에 */
	public static final int CLOSED_KEEP_BATCH = 5_000;

	private AssignmentSchedule() {
	}

	/**
	 * 일시 실패 뒤 다시 판정할 수 있는 시각 — {@code now + min(FIRST_RETRY × 2^(attempts − 1), MAX_RETRY)}. 상한에 이르면 더 곱하지 않아
	 * 넘치지 않는다.
	 * @param attempts 지금까지의 일시 실패 수 — 1 이상
	 * @throws IllegalArgumentException 시도 수가 1 미만일 때
	 */
	public static Instant retryAt(Instant now, int attempts) {
		if (attempts < 1) {
			throw new IllegalArgumentException("시도 수는 1 이상이다: " + attempts);
		}
		Duration delay = FIRST_RETRY;
		for (int i = 1; i < attempts && delay.compareTo(MAX_RETRY) < 0; i++) {
			delay = delay.multipliedBy(2);
		}
		return now.plus((delay.compareTo(MAX_RETRY) < 0) ? delay : MAX_RETRY);
	}

}
