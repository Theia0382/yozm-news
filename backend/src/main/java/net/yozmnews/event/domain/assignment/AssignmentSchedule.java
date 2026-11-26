package net.yozmnews.event.domain.assignment;

import java.time.Duration;

import net.yozmnews.evaluation.EvaluatorTiming;
import net.yozmnews.evaluation.EventDescriptor;

/**
 * 배정 대기 · 재판정 · 병합 회차와 사건 정비의 상수 — 점유 기간 · 뭉치 크기 · 일시 실패의 시도 상한 · 서술 기사 수 · 닫힌 사건 일괄 유지 수 · 병합 뭉치 크기 · 사건 정비가 한 번에 다루는 행 수의 정본 · docs/specs/event.md §4 · event-R13 · event-R16 · event-R18.
 * <p>
 * 값은 모두 측정 전 목표다. 일시 실패 · 병합 미룸 뒤의 간격은 {@link EvaluatorTiming#retryDelay} 가 정본이다.
 */
public final class AssignmentSchedule {

	/** 배정 뭉치 하나가 점유하는 대기 행 수의 상한 — 회차는 뭉치를 되풀이한다(docs/specs/platform.md 반복 회차) */
	public static final int BATCH_SIZE = 32;

	/** 점유 기간에서 판정을 기다리는 시간 밖의 여유 — 후보 고르기 · 배정 쓰기 */
	private static final Duration LEASE_MARGIN = Duration.ofMinutes(4);

	/**
	 * 배정 뭉치가 대기 행을 점유하는 기간 — 만료되면 다른 뭉치가 다시 가져간다. 뭉치 하나가 기사마다 판정 한 번을 가장 길게 기다려도
	 * 안에 든다: {@link EvaluatorTiming#LONGEST_JUDGEMENT} × {@link #BATCH_SIZE} + 여유
	 */
	public static final Duration LEASE = EvaluatorTiming.LONGEST_JUDGEMENT.multipliedBy(BATCH_SIZE).plus(LEASE_MARGIN);

	/** 일시 실패의 시도 상한 — 이 번째 일시 실패에서 판정 실패로 끝낸다 */
	public static final int MAX_ATTEMPTS = 5;

	/** 사건 소속 판정에 후보 사건마다 넘기는 서술 기사 수 — 서술 기사 상한({@link EventDescriptor#MAX_ARTICLES}) 이하 */
	public static final int DESCRIBED_ARTICLES = 3;

	/** 재판정 뭉치 하나가 다시 판정하는 배정 수의 상한 */
	public static final int REJUDGE_BATCH = 200;

	/** 재판정 뭉치 하나가 닫힌 사건의 배정을 기준만 새것으로 적는 수의 상한 — 하나씩 다시 판정하는 대상 앞을 막지 않게 한꺼번에 */
	public static final int CLOSED_KEEP_BATCH = 5_000;

	/**
	 * 병합 뭉치 하나가 보는 바뀐 사건 수의 상한 — 사건마다 판정 한 번이라 뭉치가 판정을 기다리는 시간은 이 수 ×
	 * {@link EvaluatorTiming#LONGEST_JUDGEMENT} 이하다
	 */
	public static final int MERGE_BATCH = 16;

	/** 사건 정비가 한 번에 읽고 지우는 행 수 — 닫을 사건 · 지울 표현 */
	public static final int CLEANUP_BATCH = 1_000;

	private AssignmentSchedule() {
	}

}
