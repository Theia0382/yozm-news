package net.yozmnews.user.adapter.out.persistence;

import java.util.Optional;

import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.AccountStatus;
import net.yozmnews.user.domain.session.SessionAccount;
import net.yozmnews.user.domain.session.SessionAccounts;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link SessionAccounts} 의 JdbcClient 구현 — docs/specs/user.md §4 · user-R7.
 * <p>
 * 계정과 자격을 한 번의 조회로 잇는다. 자격 행이 없는 계정은 강제 변경이 아닌 것으로 읽는다 — "계정마다 자격 하나"는 DB 가 강제하지 않으므로
 * 행이 빠져도 세션이 지워지지 않게 한다. 비밀번호 해시 칸은 읽지 않는다.
 */
@Repository
class JdbcSessionAccounts implements SessionAccounts {

	private final JdbcClient jdbc;

	JdbcSessionAccounts(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<SessionAccount> findActive(UserId id) {
		return this.jdbc.sql("""
				select u.role, coalesce(c.must_change, false) as must_change
				from users u left join user_password_credentials c on c.user_id = u.id
				where u.id = :id and u.status = :active
				""")
			.param("id", id.value())
			.param("active", AccountStatus.ACTIVE.name())
			.query((rs, row) -> new SessionAccount(id, AccountRole.valueOf(rs.getString("role")),
					rs.getBoolean("must_change")))
			.optional();
	}

}
