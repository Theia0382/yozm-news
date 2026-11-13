package net.yozmnews.user.adapter.out.persistence;

import net.yozmnews.user.domain.bootstrap.FirstAdminLock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link FirstAdminLock} 의 PostgreSQL 구현 — 트랜잭션 advisory 잠금. docs/specs/user.md §4
 */
@Repository
class JdbcFirstAdminLock implements FirstAdminLock {

	/** advisory lock 의 첫 키 — 이 모듈의 잠금을 다른 잠금과 가른다('USER') */
	static final int USER_LOCK_NAMESPACE = 0x55534552;

	/** 첫 운영자 잠금의 둘째 키 */
	static final int FIRST_ADMIN_KEY = 0;

	private final JdbcClient jdbc;

	JdbcFirstAdminLock(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void lock() {
		this.jdbc.sql("select pg_advisory_xact_lock(:namespace, :key)")
			.param("namespace", USER_LOCK_NAMESPACE)
			.param("key", FIRST_ADMIN_KEY)
			.query()
			.singleRow();
	}

}
