package net.yozmnews.user.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.platform.UserId;
import net.yozmnews.source.SourceId;
import net.yozmnews.user.domain.interest.ExcludedSourceRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link ExcludedSourceRepository} 의 PostgreSQL 구현 — {@code user_excluded_sources}. docs/specs/user.md §4 · §6
 */
@Repository
class JdbcExcludedSources implements ExcludedSourceRepository {

	private final JdbcClient jdbc;

	JdbcExcludedSources(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Override
	public Set<SourceId> load(UserId user) {
		return new HashSet<>(this.jdbc.sql("select source_id from user_excluded_sources where user_id = :user")
			.param("user", user.value())
			.query((rs, n) -> new SourceId(rs.getLong(1)))
			.list());
	}

	@Override
	public void replace(UserId user, Set<SourceId> excluded, Instant now) {
		Set<SourceId> current = load(user);
		for (SourceId gone : current) {
			if (!excluded.contains(gone)) {
				this.jdbc.sql("delete from user_excluded_sources where user_id = :user and source_id = :source")
					.param("user", user.value())
					.param("source", gone.value())
					.update();
			}
		}
		for (SourceId added : excluded) {
			if (!current.contains(added)) {
				this.jdbc.sql("""
						insert into user_excluded_sources (user_id, source_id, created_at) values (:user, :source, :at)
						""").param("user", user.value()).param("source", added.value()).param("at", Timestamp.from(now)).update();
			}
		}
	}

	@Override
	public int forget(SourceId source) {
		return this.jdbc.sql("delete from user_excluded_sources where source_id = :source")
			.param("source", source.value())
			.update();
	}

}
