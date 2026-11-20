package net.yozmnews.feedback.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.feedback.domain.record.FeedbackRepository;
import net.yozmnews.feedback.domain.record.HistoryRow;
import net.yozmnews.feedback.domain.record.LastChange;
import net.yozmnews.platform.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link FeedbackRepository} 의 JdbcClient 구현 — docs/specs/feedback.md §4 · feedback-R1 · feedback-R3 ~ feedback-R5.
 */
@Repository
class JdbcFeedbackRepository implements FeedbackRepository {

	private final JdbcClient jdbc;

	JdbcFeedbackRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean change(UserId user, EventId event, FeedbackValue value, long digestId, Instant now) {
		return switch (value) {
			case NONE -> this.jdbc.sql("""
					delete from feedback where user_id = :user and event_id = :event
					returning 1
					""").param("user", user.value()).param("event", event.value()).query(Integer.class).optional()
				.isPresent();
			case LIKE, DISLIKE -> this.jdbc.sql("""
					insert into feedback (user_id, event_id, value, digest_id, updated_at)
					values (:user, :event, :value, :digest, :at)
					on conflict (user_id, event_id) do update
					    set value = excluded.value, digest_id = excluded.digest_id, updated_at = excluded.updated_at
					    where feedback.value <> excluded.value
					returning 1
					""")
				.param("user", user.value())
				.param("event", event.value())
				.param("value", value.name())
				.param("digest", digestId)
				.param("at", utc(now))
				.query(Integer.class)
				.optional()
				.isPresent();
		};
	}

	@Override
	public void append(UserId user, EventId event, FeedbackValue value, long digestId, Instant now) {
		this.jdbc.sql("""
				insert into feedback_events (user_id, event_id, value, digest_id, occurred_at)
				values (:user, :event, :value, :digest, :at)
				""")
			.param("user", user.value())
			.param("event", event.value())
			.param("value", value.name())
			.param("digest", digestId)
			.param("at", utc(now))
			.update();
	}

	@Override
	public List<LastChange> lastChanges(UserId user, Collection<EventId> events) {
		if (events.isEmpty()) {
			return List.of();
		}
		return this.jdbc.sql("""
				select distinct on (event_id) event_id, id, value from feedback_events
				where user_id = :user and event_id = any(:events)
				order by event_id, id desc
				""")
			.param("user", user.value())
			.param("events", ids(events))
			.query((rs, row) -> new LastChange(new EventId(rs.getLong("event_id")), rs.getLong("id"),
					FeedbackValue.valueOf(rs.getString("value"))))
			.list();
	}

	@Override
	public List<HistoryRow> history(UserId user, Instant from, Instant to) {
		return this.jdbc.sql("""
				select event_id, value, digest_id, occurred_at from feedback_events
				where user_id = :user and occurred_at >= :from and occurred_at < :to
				order by id
				""")
			.param("user", user.value())
			.param("from", utc(from))
			.param("to", utc(to))
			.query((rs, row) -> new HistoryRow(new EventId(rs.getLong("event_id")),
					FeedbackValue.valueOf(rs.getString("value")), rs.getLong("digest_id"),
					rs.getObject("occurred_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	@Override
	public void removeAll(Collection<EventId> events) {
		if (events.isEmpty()) {
			return;
		}
		Long[] ids = ids(events);
		this.jdbc.sql("delete from feedback where event_id = any(:ids)").param("ids", ids).update();
		this.jdbc.sql("delete from feedback_events where event_id = any(:ids)").param("ids", ids).update();
	}

	private static Long[] ids(Collection<EventId> events) {
		return events.stream().map(EventId::value).distinct().toArray(Long[]::new);
	}

	private static OffsetDateTime utc(Instant value) {
		return value.atOffset(ZoneOffset.UTC);
	}

}
