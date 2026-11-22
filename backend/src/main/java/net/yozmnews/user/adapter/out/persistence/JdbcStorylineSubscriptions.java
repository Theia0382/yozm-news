package net.yozmnews.user.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.StoredSubscriptionChange;
import net.yozmnews.user.domain.subscription.SubscriptionChange;
import net.yozmnews.user.domain.subscription.SubscriptionChangeCursor;
import net.yozmnews.user.domain.subscription.UserSubscriptionChange;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link StorylineSubscriptionRepository} 의 PostgreSQL 구현 — 이력 {@code user_storyline_subscription_events}.
 * docs/specs/user.md §4 · §6
 */
@Repository
class JdbcStorylineSubscriptions implements StorylineSubscriptionRepository {

	private final JdbcClient jdbc;

	JdbcStorylineSubscriptions(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Override
	public void record(UserId user, List<SubscriptionChange> changes, StorylineSubscriptionCause cause, Instant at) {
		for (SubscriptionChange change : changes) {
			this.jdbc.sql("""
					insert into user_storyline_subscription_events (user_id, storyline_id, action, cause, occurred_at)
					values (:user, :storyline, :action, :cause, :at)
					""")
				.param("user", user.value())
				.param("storyline", change.storylineId())
				.param("action", change.action().name())
				.param("cause", cause.name())
				.param("at", utc(at))
				.update();
		}
	}

	@Override
	public List<StoredSubscriptionChange> changes(UserId user, Instant from, Instant to) {
		return this.jdbc.sql("""
				select storyline_id, action, cause, occurred_at from user_storyline_subscription_events
				where user_id = :user and occurred_at >= :from and occurred_at < :to
				order by occurred_at, id
				""")
			.param("user", user.value())
			.param("from", utc(from))
			.param("to", utc(to))
			.query((rs, n) -> new StoredSubscriptionChange(rs.getLong("storyline_id"), action(rs),
					StorylineSubscriptionCause.valueOf(rs.getString("cause")), occurredAt(rs)))
			.list();
	}

	@Override
	public List<UserSubscriptionChange> userChanges(UserId user, @Nullable SubscriptionChangeCursor before,
			int limit) {
		String olderThan = (before == null) ? "" : "and (occurred_at, id) < (:at, :id)";
		JdbcClient.StatementSpec statement = this.jdbc.sql("""
				select id, storyline_id, action, occurred_at from user_storyline_subscription_events
				where user_id = :user and cause = 'USER' %s
				order by occurred_at desc, id desc
				limit :limit
				""".formatted(olderThan)).param("user", user.value()).param("limit", limit);
		if (before != null) {
			statement = statement.param("at", utc(before.occurredAt())).param("id", before.id());
		}
		return statement
			.query((rs, n) -> new UserSubscriptionChange(rs.getLong("id"), rs.getLong("storyline_id"), action(rs),
					occurredAt(rs)))
			.list();
	}

	private static StorylineSubscriptionAction action(ResultSet rs) throws SQLException {
		return StorylineSubscriptionAction.valueOf(rs.getString("action"));
	}

	private static Instant occurredAt(ResultSet rs) throws SQLException {
		return rs.getObject("occurred_at", OffsetDateTime.class).toInstant();
	}

	private static OffsetDateTime utc(Instant value) {
		return value.atOffset(ZoneOffset.UTC);
	}

}
