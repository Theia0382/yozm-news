package net.yozmnews.user.adapter.out.persistence;

import java.util.Objects;

import net.yozmnews.user.domain.interest.PreferenceVersionFence;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link PreferenceVersionFence} 의 PostgreSQL 구현 — 트랜잭션 advisory 잠금(공유 · 배타). docs/specs/user.md §4 · user-R43 · user-R47
 */
@Repository
class JdbcPreferenceVersionFence implements PreferenceVersionFence {

	/** advisory lock 의 첫 키 — 이 모듈의 잠금을 다른 잠금과 가른다('USER') */
	static final int USER_LOCK_NAMESPACE = 0x55534552;

	/** 선호 버전 울타리의 둘째 키 */
	static final int PREFERENCE_VERSION_KEY = 1;

	private final JdbcClient jdbc;

	private final TransactionTemplate ownTransaction;

	JdbcPreferenceVersionFence(JdbcClient jdbc, PlatformTransactionManager transactions) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
		this.ownTransaction = new TransactionTemplate(transactions);
		this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Override
	public void enterShared() {
		this.jdbc.sql("select pg_advisory_xact_lock_shared(:namespace, :key)")
			.param("namespace", USER_LOCK_NAMESPACE)
			.param("key", PREFERENCE_VERSION_KEY)
			.query()
			.singleRow();
	}

	@Override
	public void passExclusive() {
		this.ownTransaction.executeWithoutResult(status -> this.jdbc.sql("select pg_advisory_xact_lock(:namespace, :key)")
			.param("namespace", USER_LOCK_NAMESPACE)
			.param("key", PREFERENCE_VERSION_KEY)
			.query()
			.singleRow());
	}

}
