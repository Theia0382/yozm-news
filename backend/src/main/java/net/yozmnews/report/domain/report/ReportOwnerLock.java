package net.yozmnews.report.domain.report;

import net.yozmnews.platform.UserId;

/**
 * 사용자 하나의 리포트 예약을 차례로 세우는 잠금 — 트랜잭션이 끝나면 풀린다. 일정 회차가 가장 늦은 끝을 다시 확인하기 전에 잡는다.
 * docs/specs/report.md §4
 */
public interface ReportOwnerLock {

	/** 그 사용자의 잠금을 잡는다 — 트랜잭션 안에서 부른다 */
	void lock(UserId user);

}
