package net.yozmnews.user.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

import net.yozmnews.user.domain.throttle.EmailDigest;
import net.yozmnews.user.domain.throttle.FailureRecord;
import net.yozmnews.user.domain.throttle.LoginFailures;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link LoginFailures} 의 JdbcClient 구현 — docs/specs/user.md §4 · user-R18 · user-R19.
 * <p>
 * 잠금은 자리 행을 {@code INSERT … ON CONFLICT DO NOTHING} 으로 넣은 뒤 {@code SELECT … FOR UPDATE} 다 — 위반 예외는 트랜잭션을 깨므로
 * 쓰지 않는다. 같은 이메일의 동시 시도는 이 잠금에서 줄을 선다.
 */
@Repository
class JdbcLoginFailures implements LoginFailures {

	private final JdbcClient jdbc;

	JdbcLoginFailures(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public FailureRecord lock(EmailDigest digest) {
		this.jdbc.sql("""
				insert into login_failures (email_digest, failures, last_failed_at) values (:digest, 0, null)
				on conflict (email_digest) do nothing
				""").param("digest", digest.value()).update();
		return this.jdbc.sql("""
				select failures, last_failed_at from login_failures where email_digest = :digest for update
				""")
			.param("digest", digest.value())
			.query((rs, row) -> new FailureRecord(rs.getInt("failures"),
					instant(rs.getObject("last_failed_at", OffsetDateTime.class))))
			.single();
	}

	@Override
	public void record(EmailDigest digest, FailureRecord record) {
		this.jdbc.sql("""
				update login_failures set failures = :failures, last_failed_at = :lastFailedAt
				where email_digest = :digest
				""")
			.param("digest", digest.value())
			.param("failures", record.failures())
			.param("lastFailedAt", timestamp(record.lastFailedAt()))
			.update();
	}

	@Override
	public void forget(EmailDigest digest) {
		this.jdbc.sql("delete from login_failures where email_digest = :digest").param("digest", digest.value()).update();
	}

	@Override
	public int forgetOlderThan(Instant cutoff) {
		return this.jdbc.sql("delete from login_failures where last_failed_at < :cutoff")
			.param("cutoff", Timestamp.from(cutoff))
			.update();
	}

	private static @Nullable Instant instant(@Nullable OffsetDateTime value) {
		return (value != null) ? value.toInstant() : null;
	}

	private static @Nullable Timestamp timestamp(@Nullable Instant value) {
		return (value != null) ? Timestamp.from(value) : null;
	}

}
