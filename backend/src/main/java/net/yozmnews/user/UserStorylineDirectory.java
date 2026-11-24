package net.yozmnews.user;

import java.time.Instant;
import java.util.List;

import net.yozmnews.platform.UserId;

/**
 * 사용자 스토리라인 조회 — 사용자가 관심 신호(좋아요 · 싫어요 · 흐름 추적)를 준 흐름과 그것이 추적하는 공용 스토리라인. 다이제스트의 관심도와
 * 후보 선별, 변화 리포트의 재료 조립이 읽는다. docs/specs/user.md §3
 * <p>
 * 사용자를 인자로 받는 조회다 — 화면 경로에서는 세션 사용자로만 부른다. 쓰지 않는다 — 저장 행과 신호 이력 · 지금의 스토리라인 관계로 맞추기
 * 결과를 계산해 낸다. 저장 행은 사용자의 추적 · 해제 · 조회와 스토리라인 합치기 · 삭제 알림이 고쳐 쓴다. 잠그지 않으므로 그 순간의 값이다.
 */
public interface UserStorylineDirectory {

	/**
	 * 창 [{@code from}, {@code to}) 의 신호와 지금 관계로 낸 그 사용자의 사용자 스토리라인. 추적 중인 사용자 스토리라인은 창과 무관하게
	 * 든다.
	 * @return 사용자 스토리라인 — 헤드 ID 순. 없으면 빈 목록
	 * @throws IllegalArgumentException {@code from} 이 {@code to} 보다 늦을 때
	 */
	List<UserStorylineView> of(UserId user, Instant from, Instant to);

}
