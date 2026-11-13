package net.yozmnews.user.domain.bootstrap;

/**
 * 첫 운영자 판정 · 생성의 잠금 — 트랜잭션 advisory 잠금. 함께 기동한 프로세스들이 줄을 서 하나만 만든다. 구현은
 * {@code adapter/out/persistence} 의 {@code JdbcFirstAdminLock}. docs/specs/user.md §4
 */
public interface FirstAdminLock {

	/** 잠근다 — 트랜잭션 안에서 부르고, 트랜잭션이 끝나면 풀린다 */
	void lock();

}
