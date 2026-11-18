package net.yozmnews.user;

import java.util.Optional;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.UserId;

/**
 * 다이제스트를 만들 때 쓸 모델 — 다이제스트의 일정 회차 · 생성 작업 · 수동 생성이 읽는다. docs/specs/user.md §3 · user-R51
 * <p>
 * 사용자를 인자로 받는 조회다 — 화면 경로에서는 세션 사용자로만 부른다. 쓰지 않는다. 판정은 관심 설정 화면의 쓸 모델과 같은 정본
 * ({@code ModelChoice})이다.
 */
public interface DigestModelDirectory {

	/** 그 사용자의 쓸 모델 — 계정이 없거나 쓰는({@code ACTIVE}) 계정이 아니거나 쓸 모델이 없으면 빈 값 */
	Optional<LlmModel> modelOf(UserId user);

}
