package net.yozmnews.report.adapter.out.persistence;

import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.report.domain.report.ReportOwnerLock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@link ReportOwnerLock} 의 PostgreSQL 구현 — 트랜잭션 advisory 잠금. docs/specs/report.md §4
 */
@Component
class JdbcReportOwnerLock implements ReportOwnerLock {

	/** 리포트 사용자 잠금의 첫째 키 — 'RPRT'. 다른 모듈의 advisory 잠금과 겹치지 않는다 */
	static final int REPORT_OWNER_LOCK_NAMESPACE = 0x52505254;

	private final JdbcClient jdbc;

	JdbcReportOwnerLock(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	/** 둘째 키는 사용자 ID 의 해시다 — 충돌하면 직렬화가 늘 뿐 결과는 틀리지 않는다 */
	@Override
	public void lock(UserId user) {
		this.jdbc.sql("select pg_advisory_xact_lock(:namespace, :key)")
			.param("namespace", REPORT_OWNER_LOCK_NAMESPACE)
			.param("key", Long.hashCode(user.value()))
			.query()
			.singleRow();
	}

}
