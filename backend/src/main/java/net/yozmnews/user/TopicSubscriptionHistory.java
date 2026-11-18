package net.yozmnews.user;

import java.time.Instant;

import net.yozmnews.platform.UserId;

/**
 * 주제 구독 이력 조회 — 다이제스트의 관심도 작업이 읽는다. docs/specs/user.md §3 · user-R58
 * <p>
 * 사용자를 인자로 받는 조회다 — 화면 경로에서는 세션 사용자로만 부른다. 쓰지 않는다 — 이력 행의 키는 기록 당시 버전의 것이라 그 버전의 구조로
 * 펼치고 계보로 활성 버전의 중분류로 풀어 낸다. 기간을 사용자 시간대의 날로 자르는 일과 관심 신호로 쓸 행을 고르는 일은 부르는 쪽이 한다.
 */
public interface TopicSubscriptionHistory {

	/**
	 * 선호 이력 가운데 주제 행으로 시각이 {@code from} 이상 · {@code to} 미만인 것 — 시각 · 행 순. 계정이 없으면 빈 목록이다.
	 * @throws IllegalArgumentException {@code from} 이 {@code to} 보다 늦을 때
	 */
	TopicSubscriptionChanges changes(UserId user, Instant from, Instant to);

}
