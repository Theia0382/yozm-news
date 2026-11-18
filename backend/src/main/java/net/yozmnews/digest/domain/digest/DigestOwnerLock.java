package net.yozmnews.digest.domain.digest;

import net.yozmnews.platform.UserId;

/**
 * 한 사용자의 다이제스트 행 만들기 잠금 — 트랜잭션 advisory 잠금. 같은 사용자의 일정 행 만들기 · 수동 생성이 겹치면 줄을 서 차례로 확인하고
 * 만든다. 구현은 {@code adapter/out/persistence} 의 {@code JdbcDigestOwnerLock}. docs/specs/digest.md §4
 */
public interface DigestOwnerLock {

	/** 이 사용자를 잠근다 — 트랜잭션 안에서 부르고, 트랜잭션이 끝나면 풀린다 */
	void lock(UserId user);

}
