package net.yozmnews.user.adapter.out.persistence;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.ChangeActor;
import net.yozmnews.user.SignUpPolicyChange;
import net.yozmnews.user.domain.signup.SignUpPolicyRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link SignUpPolicyRepository} 의 JdbcClient 구현 — docs/specs/user.md §4 · §6.
 * <p>
 * 처음 값은 고정 키 행의 {@code INSERT … ON CONFLICT DO NOTHING} 이라 두 프로세스가 동시에 넣어도 한 행 · 한 이력이다. 변경은 행 잠금 뒤다.
 */
@Repository
class JdbcSignUpPolicy implements SignUpPolicyRepository {

	private final JdbcClient jdbc;

	JdbcSignUpPolicy(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean seedIfAbsent(Instant now) {
		int inserted = this.jdbc.sql("""
				insert into user_sign_up_policy (id, open, changed_at) values (1, :open, :now)
				on conflict (id) do nothing
				""").param("open", INITIAL_OPEN).param("now", utc(now)).update();
		if (inserted == 0) {
			return false;
		}
		history(null, INITIAL_OPEN, ChangeActor.SYSTEM, now);
		return true;
	}

	@Override
	public Optional<Boolean> open() {
		return this.jdbc.sql("select open from user_sign_up_policy where id = 1").query(Boolean.class).optional();
	}

	@Override
	public boolean lockOpen() {
		return this.jdbc.sql("select open from user_sign_up_policy where id = 1 for update")
			.query(Boolean.class)
			.optional()
			.orElseThrow(() -> new IllegalStateException("가입 정책의 처음 값이 없다"));
	}

	@Override
	public void change(boolean previous, boolean open, ChangeActor actor, Instant now) {
		this.jdbc.sql("update user_sign_up_policy set open = :open, changed_at = :now where id = 1")
			.param("open", open)
			.param("now", utc(now))
			.update();
		history(previous, open, actor, now);
	}

	@Override
	public List<SignUpPolicyChange> changes() {
		return this.jdbc.sql("""
				select previous_open, open, actor, changed_at from user_sign_up_policy_changes
				order by changed_at desc, id desc
				""")
			.query((rs, row) -> new SignUpPolicyChange(rs.getObject("previous_open", Boolean.class),
					rs.getBoolean("open"), ChangeActor.valueOf(rs.getString("actor")),
					rs.getObject("changed_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	private void history(@Nullable Boolean previous, boolean open, ChangeActor actor, Instant now) {
		this.jdbc.sql("""
				insert into user_sign_up_policy_changes (previous_open, open, actor, changed_at)
				values (:previous, :open, :actor, :now)
				""")
			.param("previous", previous, Types.BOOLEAN)
			.param("open", open)
			.param("actor", actor.name())
			.param("now", utc(now))
			.update();
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
