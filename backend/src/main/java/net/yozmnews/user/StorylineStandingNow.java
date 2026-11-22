package net.yozmnews.user;

import java.util.Optional;

import net.yozmnews.platform.UserId;

/**
 * 사용자 하나의 지금 흐름 판정 — 이 모듈이 선언하고 {@code digest} 가 구현한다. 사용자 스토리라인의 정리 작업이 만들 흐름 · 남길 흐름을 가리려고
 * 부른다. docs/specs/user.md §3
 * <p>
 * 이 모듈은 관심을 계산하지 않고 판정의 기준을 갖지 않으며 {@code digest} 의 타입을 쓰지 않는다. 사용자를 인자로 받는다 — 백그라운드 작업이
 * 그 작업의 사용자로 부른다. 쓰지 않는다.
 */
public interface StorylineStandingNow {

	/**
	 * @return 지금을 기준 시각으로 둔 관심 흐름 · 남길 흐름의 꼭대기. 계정이 없으면 빈 값
	 * @throws IllegalStateException 재료의 태그 체계 활성 버전이 거듭 어긋나 판정을 내지 못했을 때
	 */
	Optional<StorylineStanding> standingOf(UserId user);

}
