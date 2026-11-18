package net.yozmnews.user;

import net.yozmnews.platform.UserId;

/**
 * 구독 제안 거절 조회 — 다이제스트의 구독 제안이 다시 내지 않을 주제를 고를 때 읽는다. docs/specs/user.md §3 · user-R65
 * <p>
 * 사용자를 인자로 받는 조회다 — 화면 경로에서는 세션 사용자로만 부른다. 쓰지 않는다.
 */
public interface TopicSuggestionDeclines {

	/**
	 * @return 그 사용자의 거절을 활성 버전의 주제 중분류로 푼 것 — 계보 없이 지운 키는 빠지고, 같은 중분류에 닿는 행이 여럿이면 거절 시각이
	 *     늦은 것 하나다. 계정이 없으면 빈 목록
	 */
	TopicSuggestionDeclineList declinesOf(UserId user);

}
