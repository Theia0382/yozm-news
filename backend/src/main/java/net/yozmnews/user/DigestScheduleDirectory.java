package net.yozmnews.user;

import java.util.List;

/**
 * 다이제스트 일정 목록 — 다이제스트의 일정 회차가 생성 시각이 된 사용자를 고르려고 읽는다. docs/specs/user.md §3
 * <p>
 * 백그라운드 작업 전용이다 — 사용자 범위가 없어 화면 경로에서 부르지 않는다. 쓰지 않는다. 쓰는({@code ACTIVE}) 계정 가운데 설정 행이 있는
 * 사용자만 낸다 — 설정 행은 계정을 만들 때 함께 쓰이고 기동 때 채워지므로(user-R43 · R44) 쓰는 계정은 행이 있다. 날짜 · 생성 시각 계산은
 * 부르는 쪽이 한다.
 */
public interface DigestScheduleDirectory {

	/** 쪽 하나의 상한 — 측정 전 목표 */
	int MAX_PAGE = 500;

	/**
	 * 사용자 ID 가 {@code afterUserId} 보다 큰 사용자의 일정 — ID 순으로 {@code limit} 개까지.
	 * @throws IllegalArgumentException {@code limit} 이 1 ~ {@link #MAX_PAGE} 밖일 때
	 */
	List<DigestScheduleEntry> page(long afterUserId, int limit);

}
