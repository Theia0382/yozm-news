package net.yozmnews.user.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.recovery.RecoveryCodeRepository;
import net.yozmnews.user.domain.secret.SecretDigest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link RecoveryCodeRepository} 의 JdbcClient 구현 — docs/specs/user.md §4 · §6. 계정마다 한 행을 새 코드로 덮어쓴다.
 */
@Repository
class JdbcRecoveryCodes implements RecoveryCodeRepository {

	private final JdbcClient jdbc;

	JdbcRecoveryCodes(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void replace(UserId user, SecretDigest code, Instant issuedAt) {
		this.jdbc.sql("""
				insert into user_recovery_codes (user_id, code_digest, issued_at) values (:user, :code, :issuedAt)
				on conflict (user_id) do update set code_digest = excluded.code_digest, issued_at = excluded.issued_at
				""")
			.param("user", user.value())
			.param("code", code.value())
			.param("issuedAt", Timestamp.from(issuedAt))
			.update();
	}

	@Override
	public Optional<SecretDigest> find(UserId user) {
		return this.jdbc.sql("select code_digest from user_recovery_codes where user_id = :user")
			.param("user", user.value())
			.query((rs, row) -> SecretDigest.stored(rs.getBytes("code_digest")))
			.optional();
	}

}
