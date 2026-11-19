package net.yozmnews.event.domain.assignment;

import java.time.Duration;

/**
 * 배정 대기 · 재판정 · 병합 회차와 정리 작업의 상수 — 점유 기간 · 회차 크기 · 일시 실패의 시도 상한 · 서술 기사 수 · 닫힌 사건 일괄 유지 수 · 병합 회차 크기 · 정리 묶음 크기의 정본 · docs/specs/event.md §4 · event-R13 · event-R16 · event-R18.
 * <p>
 * 값은 모두 측정 전 목표다. 일시 실패 · 병합 미룸 뒤의 간격은 {@code evaluation} 의 {@code EvaluatorTiming#retryDelay} 가 정본이다. 점유 기간은 회차 한 묶음이 기사마다 판정 요청 둘을 마감(15초)까지 기다려도 안에 들게 잡는다 — 32 × 2 × 15초 =
 * 16분 &lt; 20분.
 */
public final class AssignmentSchedule {

	/** 배정 회차 하나가 점유하는 대기 행 수의 상한 */
	public static final int BATCH_SIZE = 32;

	/** 배정 회차가 대기 행을 점유하는 기간 — 만료되면 다른 회차가 다시 가져간다 */
	public static final Duration LEASE = Duration.ofMinutes(20);

	/** 일시 실패의 시도 상한 — 이 번째 일시 실패에서 판정 실패로 끝낸다 */
	public static final int MAX_ATTEMPTS = 5;

	/** 사건 소속 판정에 후보 사건마다 넘기는 서술 기사 수 — 사건 소속 판정의 상한(5) 이하 */
	public static final int DESCRIBED_ARTICLES = 3;

	/** 재판정 회차 하나가 다시 판정하는 배정 수의 상한 */
	public static final int REJUDGE_BATCH = 200;

	/** 재판정 회차 하나가 닫힌 사건의 배정을 기준만 새것으로 적는 수의 상한 — 하나씩 다시 판정하는 대상 앞을 막지 않게 한꺼번에 */
	public static final int CLOSED_KEEP_BATCH = 5_000;

	/** 병합 회차 하나가 보는 바뀐 사건 수의 상한 — 사건마다 판정 요청 하나 · 마감 15초면 4분 이하 */
	public static final int MERGE_BATCH = 16;

	/** 정리 작업이 한 번에 읽고 지우는 행 수 — 닫을 사건 · 지울 표현 */
	public static final int CLEANUP_BATCH = 1_000;

	private AssignmentSchedule() {
	}

}
