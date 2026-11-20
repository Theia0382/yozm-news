package net.yozmnews.feedback.adapter.out.persistence;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.feedback.domain.record.ItemRecordRepository;
import net.yozmnews.platform.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link ItemRecordRepository} 의 JdbcClient 구현 — docs/specs/feedback.md §4 · feedback-R1 · feedback-R7.
 */
@Repository
class JdbcItemRecordRepository implements ItemRecordRepository {

	private final JdbcClient jdbc;

	JdbcItemRecordRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean change(UserId user, long digestId, EventId itemKey, FeedbackValue value, Instant now) {
		return switch (value) {
			case NONE -> this.jdbc.sql("""
					delete from feedback_items where user_id = :user and digest_id = :digest and item_event_id = :item
					returning 1
					""")
				.param("user", user.value())
				.param("digest", digestId)
				.param("item", itemKey.value())
				.query(Integer.class)
				.optional()
				.isPresent();
			case LIKE, DISLIKE -> this.jdbc.sql("""
					insert into feedback_items (user_id, digest_id, item_event_id, value, updated_at)
					values (:user, :digest, :item, :value, :at)
					on conflict (user_id, digest_id, item_event_id) do update
					    set value = excluded.value, updated_at = excluded.updated_at
					    where feedback_items.value <> excluded.value
					returning 1
					""")
				.param("user", user.value())
				.param("digest", digestId)
				.param("item", itemKey.value())
				.param("value", value.name())
				.param("at", now.atOffset(ZoneOffset.UTC))
				.query(Integer.class)
				.optional()
				.isPresent();
		};
	}

	@Override
	public Map<EventId, FeedbackValue> valuesOf(UserId user, long digestId) {
		Map<EventId, FeedbackValue> values = new LinkedHashMap<>();
		this.jdbc.sql("""
				select item_event_id, value from feedback_items
				where user_id = :user and digest_id = :digest
				order by item_event_id
				""").param("user", user.value()).param("digest", digestId).query((ResultSet rs) -> {
					values.put(new EventId(rs.getLong("item_event_id")), FeedbackValue.valueOf(rs.getString("value")));
				});
		return Collections.unmodifiableMap(values);
	}

	@Override
	public void removeAll(Map<Long, ? extends Collection<EventId>> itemKeysByDigest) {
		itemKeysByDigest.forEach((digest, keys) -> {
			if (keys.isEmpty()) {
				return;
			}
			this.jdbc.sql("delete from feedback_items where digest_id = :digest and item_event_id = any(:items)")
				.param("digest", digest)
				.param("items", keys.stream().map(EventId::value).distinct().toArray(Long[]::new))
				.update();
		});
	}

}
