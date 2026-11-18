package net.yozmnews.user;

import java.time.ZoneId;
import java.util.Optional;

import net.yozmnews.platform.UserId;

/**
 * 사용자 시간대 조회 — 관심 설정의 시간대. 다이제스트의 관심사 히스토리가 기간을 자를 때 읽는다. docs/specs/user.md §3
 * <p>
 * 사용자를 인자로 받는 조회다 — 화면 경로에서는 세션 사용자로만 부른다. 쓰지 않는다.
 */
public interface UserTimezones {

	/**
	 * @return 설정 행의 시간대 — 설정 행이 없으면 계정을 만들 때의 기본값, 계정이 없으면 빈 값
	 */
	Optional<ZoneId> timezoneOf(UserId user);

}
