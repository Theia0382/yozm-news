package net.yozmnews.account.adapter.out.persistence;

import net.yozmnews.account.domain.bootstrap.FirstAdminLock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link FirstAdminLock} 의 PostgreSQL 구현 — 트랜잭션 advisory 잠금. docs/specs/account.md §4
 */
@Repository
class JdbcFirstAdminLock implements FirstAdminLock {

	/** advisory lock 의 첫 키 — 계정 쪽 잠금을 다른 잠금과 가른다('ACCT') */
	static final int ACCOUNT_LOCK_NAMESPACE = 0x41434354;

	/** 첫 운영자 잠금의 둘째 키 */
	static final int FIRST_ADMIN_KEY = 0;

	private final JdbcClient jdbc;

	JdbcFirstAdminLock(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void lock() {
		this.jdbc.sql("select pg_advisory_xact_lock(:namespace, :key)")
			.param("namespace", ACCOUNT_LOCK_NAMESPACE)
			.param("key", FIRST_ADMIN_KEY)
			.query()
			.singleRow();
	}

}
