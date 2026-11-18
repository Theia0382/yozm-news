package net.yozmnews.digest.adapter.out.persistence;

import net.yozmnews.digest.domain.digest.DigestOwnerLock;
import net.yozmnews.platform.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link DigestOwnerLock} 의 PostgreSQL 구현 — 트랜잭션 advisory 잠금. docs/specs/digest.md §4
 */
@Repository
class JdbcDigestOwnerLock implements DigestOwnerLock {

	/** advisory lock 의 첫 키 — 이 모듈의 잠금을 다른 잠금과 가른다('DGST') */
	static final int DIGEST_OWNER_LOCK_NAMESPACE = 0x44475354;

	private final JdbcClient jdbc;

	JdbcDigestOwnerLock(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 둘째 키는 사용자 ID 의 해시다 — 충돌하면 직렬화가 늘 뿐 결과는 틀리지 않는다 */
	@Override
	public void lock(UserId user) {
		this.jdbc.sql("select pg_advisory_xact_lock(:namespace, :key)")
			.param("namespace", DIGEST_OWNER_LOCK_NAMESPACE)
			.param("key", Long.hashCode(user.value()))
			.query()
			.singleRow();
	}

}
