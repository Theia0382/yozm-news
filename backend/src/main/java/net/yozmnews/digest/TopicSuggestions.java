package net.yozmnews.digest;

import java.util.Optional;

import net.yozmnews.platform.UserId;

/**
 * 구독 제안 — 구독하지 않은 주제 중분류 가운데 지금 관심이 제안 기준 이상이고 거절에 막히지 않은 것. 저장하지 않고 부를 때 계산한다.
 * docs/specs/digest.md §3 · digest-R41 · digest-R42
 * <p>
 * 사용자를 인자로 받는다 — 화면 경로에서는 세션 사용자로만 부른다. 트랜잭션 없이 다른 모듈의 조회를 차례로 부르고, 조회 사이의 태그 체계 활성
 * 전환으로 키 버전이 어긋나면 처음부터 다시 한다(끝내 어긋나면 {@link IllegalStateException}).
 */
public interface TopicSuggestions {

	/**
	 * @return 지금 제안할 주제 — 계정이 없으면 빈 값
	 */
	Optional<TopicSuggestionList> suggestionsOf(UserId user);

}
