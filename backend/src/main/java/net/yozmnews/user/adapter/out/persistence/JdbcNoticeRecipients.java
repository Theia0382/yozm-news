package net.yozmnews.user.adapter.out.persistence;

import java.util.List;

import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.AccountStatus;
import net.yozmnews.user.domain.notice.NoticeRecipients;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link NoticeRecipients} 의 JdbcClient 구현 — 사용자 범위가 없는 조회라 리스너만 쓴다. docs/specs/user.md §4 · user-R22
 */
@Repository
class JdbcNoticeRecipients implements NoticeRecipients {

	private final JdbcClient jdbc;

	JdbcNoticeRecipients(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<UserId> activeAdmins() {
		return this.jdbc.sql("select id from users where role = :role and status = :status order by id")
			.param("role", AccountRole.ADMIN.name())
			.param("status", AccountStatus.ACTIVE.name())
			.query(Long.class)
			.list()
			.stream()
			.map(UserId::new)
			.toList();
	}

}
