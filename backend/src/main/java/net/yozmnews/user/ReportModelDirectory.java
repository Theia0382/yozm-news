package net.yozmnews.user;

import java.util.Optional;

import net.yozmnews.platform.UserId;

/**
 * 변화 리포트를 만들 때 쓸 모델과 추론 강도 — 리포트의 일정 회차 · 생성 작업 · 다시 만들기가 읽는다. docs/specs/user.md §3 · user-R51 ·
 * user-R59
 * <p>
 * 사용자를 인자로 받는 조회다 — 화면 경로에서는 세션 사용자로만 부른다. 쓰지 않는다. 쓸 모델의 판정은 다이제스트의 쓸 모델과 같은 정본
 * ({@code ModelChoice})이다.
 */
public interface ReportModelDirectory {

	/** 그 사용자의 쓸 모델과 추론 강도 — 계정이 없거나 쓰는({@code ACTIVE}) 계정이 아니거나 쓸 모델이 없으면 빈 값 */
	Optional<ReportModel> modelOf(UserId user);

}
