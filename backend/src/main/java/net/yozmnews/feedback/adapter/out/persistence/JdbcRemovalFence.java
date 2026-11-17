package net.yozmnews.feedback.adapter.out.persistence;

import net.yozmnews.feedback.domain.record.RemovalFence;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link RemovalFence} 의 PostgreSQL 구현 — 트랜잭션 advisory 잠금(공유 · 배타). docs/specs/feedback.md §4 · feedback-R6
 */
@Repository
class JdbcRemovalFence implements RemovalFence {

	/** advisory lock 의 첫 키 — 이 모듈의 잠금을 다른 잠금과 가른다('FDBF') */
	static final int FEEDBACK_LOCK_NAMESPACE = 0x46444246;

	/** 울타리의 둘째 키 */
	static final int REMOVAL_FENCE_KEY = 1;

	private final JdbcClient jdbc;

	JdbcRemovalFence(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void enterShared() {
		this.jdbc.sql("select pg_advisory_xact_lock_shared(:namespace, :key)")
			.param("namespace", FEEDBACK_LOCK_NAMESPACE)
			.param("key", REMOVAL_FENCE_KEY)
			.query()
			.singleRow();
	}

	@Override
	public void enterExclusive() {
		this.jdbc.sql("select pg_advisory_xact_lock(:namespace, :key)")
			.param("namespace", FEEDBACK_LOCK_NAMESPACE)
			.param("key", REMOVAL_FENCE_KEY)
			.query()
			.singleRow();
	}

}
