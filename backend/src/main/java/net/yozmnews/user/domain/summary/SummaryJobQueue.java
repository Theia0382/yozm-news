package net.yozmnews.user.domain.summary;

import net.yozmnews.platform.UserId;

/**
 * 정리 작업의 등록 포트 — 작업 서버(외부 의존성)에 사용자 하나의 정리 작업을 넣는다. 구현은 {@code adapter/out/job} 의
 * {@code JobRunrSummaryJobQueue}. docs/specs/user.md §4
 */
public interface SummaryJobQueue {

	/**
	 * 그 사용자의 정리 작업을 등록한다 — 같은 사용자 · 다이제스트로 거듭 등록해도 작업은 하나다.
	 * @param digestId 계기가 된 다이제스트
	 */
	void enqueue(UserId user, long digestId);

}
