package net.yozmnews.user;

import java.util.List;

/**
 * 리포트 일정 목록 — 변화 리포트의 일정 회차가 리포트 시점이 된 사용자를 고르려고 읽는다. docs/specs/user.md §3 · user-R61
 * <p>
 * 백그라운드 작업 전용이다 — 사용자 범위가 없어 화면 경로에서 부르지 않는다. 쓰지 않는다. 쓰는({@code ACTIVE}) 계정마다 설정 행의 값을
 * 낸다(설정 행이 없으면 기본값). 기간 · 경계 계산은 부르는 쪽이 한다.
 */
public interface ReportScheduleDirectory {

	/** 쪽 하나의 상한 — 측정 전 목표 */
	int MAX_PAGE = 500;

	/**
	 * 사용자 ID 가 {@code afterUserId} 보다 큰 사용자의 리포트 일정 — ID 순으로 {@code limit} 개까지.
	 * @throws IllegalArgumentException {@code limit} 이 1 ~ {@link #MAX_PAGE} 밖일 때
	 */
	List<ReportScheduleEntry> page(long afterUserId, int limit);

}
