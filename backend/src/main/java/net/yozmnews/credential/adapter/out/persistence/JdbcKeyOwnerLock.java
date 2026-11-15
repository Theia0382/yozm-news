package net.yozmnews.credential.adapter.out.persistence;

import java.util.Objects;

import net.yozmnews.credential.domain.crypto.KeyOwner;
import net.yozmnews.credential.domain.key.KeyOwnerLock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link KeyOwnerLock} 의 PostgreSQL 구현 — 트랜잭션 advisory 잠금. docs/specs/credential.md §4 · credential-R11
 */
@Repository
class JdbcKeyOwnerLock implements KeyOwnerLock {

	/** advisory lock 의 첫 키 — 이 모듈의 잠금을 다른 잠금과 가른다('CRED') */
	static final int KEY_OWNER_LOCK_NAMESPACE = 0x43524544;

	private final JdbcClient jdbc;

	JdbcKeyOwnerLock(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 둘째 키는 사용자 ID 와 공급자 키의 해시다 — 충돌하면 직렬화가 늘 뿐 교체는 틀리지 않는다 */
	@Override
	public void lock(KeyOwner owner) {
		this.jdbc.sql("select pg_advisory_xact_lock(:namespace, :key)")
			.param("namespace", KEY_OWNER_LOCK_NAMESPACE)
			.param("key", Objects.hash(owner.user().value(), owner.provider().key()))
			.query()
			.singleRow();
	}

}
