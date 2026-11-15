package net.yozmnews.credential.domain.key;

import net.yozmnews.credential.domain.crypto.KeyOwner;

/**
 * 한 주인(사용자 × 공급자)의 키 교체 잠금 — 트랜잭션 advisory 잠금. 같은 주인의 등록이 겹치면 줄을 서 차례로 교체한다. 구현은
 * {@code adapter/out/persistence} 의 {@code JdbcKeyOwnerLock}. docs/specs/credential.md §4 · credential-R11
 */
public interface KeyOwnerLock {

	/** 이 주인을 잠근다 — 트랜잭션 안에서 부르고, 트랜잭션이 끝나면 풀린다 */
	void lock(KeyOwner owner);

}
