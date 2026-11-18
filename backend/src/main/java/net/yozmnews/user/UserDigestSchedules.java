package net.yozmnews.user;

import java.util.Optional;

import net.yozmnews.platform.UserId;

/**
 * 사용자 하나의 다이제스트 일정 조회 — 관심 설정의 시간대 · 생성 시각. 다이제스트의 수동 생성 상태가 오늘 일정 생성 시각을 정할 때 읽는다.
 * docs/specs/user.md §3 · user-R60
 * <p>
 * 사용자를 인자로 받는 조회다 — 화면 경로에서는 세션 사용자로만 부른다. 쓰지 않는다. 날짜 · 생성 시각 계산은 부르는 쪽이 한다.
 */
public interface UserDigestSchedules {

	/**
	 * @return 설정 행의 일정 — 설정 행이 없으면 계정을 만들 때의 기본값, 계정이 없으면 빈 값
	 */
	Optional<DigestScheduleEntry> scheduleOf(UserId user);

}
