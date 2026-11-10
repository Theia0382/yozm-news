package net.yozmnews.storyline.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;

import net.yozmnews.storyline.domain.queue.LinkingLease;
import net.yozmnews.storyline.domain.queue.LinkingQueueRepository;
import net.yozmnews.storyline.domain.queue.PendingEvent;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link LinkingQueueRepository} 의 JdbcClient 구현 — docs/specs/storyline.md §4.
 * <p>
 * 넣기는 {@code INSERT … ON CONFLICT (event_id) DO UPDATE} 로 다시 알림 수를 올리고 순서 키를 바꾼다 — 여럿을 넣을 때는 사건 ID 순이다.
 * 잠금은 {@code FOR UPDATE SKIP LOCKED}, 점유한 회차의 반영은 점유 토큰(지우기는 다시 알림 수도)을 조건으로 한 갱신 · 삭제다.
 */
@Repository
class JdbcLinkingQueueRepository implements LinkingQueueRepository {

	private final JdbcClient jdbc;

	JdbcLinkingQueueRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void enqueue(long eventId, @Nullable Instant orderAt, Instant now) {
		this.jdbc.sql("""
				insert into storyline_linking_queue (event_id, order_at, enqueued_at) values (:event, :order, :now)
				on conflict (event_id) do update
				set notifications = storyline_linking_queue.notifications + 1, order_at = excluded.order_at
				""")
			.param("event", eventId)
			.param("order", (orderAt != null) ? timestamp(orderAt) : null, Types.TIMESTAMP_WITH_TIMEZONE)
			.param("now", timestamp(now))
			.update();
	}

	@Override
	public int enqueueAll(Map<Long, Instant> orderAtByEvent, Instant now) {
		if (orderAtByEvent.isEmpty()) {
			return 0;
		}
		Map<Long, Instant> sorted = new TreeMap<>(orderAtByEvent);
		List<Boolean> inserted = this.jdbc.sql("""
				insert into storyline_linking_queue (event_id, order_at, enqueued_at)
				select t.id, t.ordered, :now
				from unnest(cast(:ids as bigint[]), cast(:orders as timestamptz[])) as t(id, ordered)
				order by t.id
				on conflict (event_id) do update
				set notifications = storyline_linking_queue.notifications + 1, order_at = excluded.order_at
				returning (xmax = 0) as inserted
				""")
			.param("ids", sorted.keySet().toArray(Long[]::new))
			.param("orders", sorted.values().stream().map(Instant::toString).toArray(String[]::new))
			.param("now", timestamp(now))
			.query(Boolean.class)
			.list();
		return (int) inserted.stream().filter(Boolean::booleanValue).count();
	}

	@Override
	public List<PendingEvent> lockDue(Instant now, int limit) {
		return this.jdbc.sql("""
				select event_id, order_at, notifications, failures, lease_token, lease_expires_at
				from storyline_linking_queue
				where lease_token is null or lease_expires_at <= :now
				order by order_at nulls first, event_id
				limit :limit
				for update skip locked
				""").param("now", timestamp(now)).param("limit", limit).query(JdbcLinkingQueueRepository::row).list();
	}

	@Override
	public void saveLocked(PendingEvent next) {
		LinkingLease lease = next.lease();
		this.jdbc.sql("""
				update storyline_linking_queue set lease_token = :token, lease_expires_at = :expires where event_id = :event
				""")
			.param("event", next.eventId())
			.param("token", (lease != null) ? lease.token() : null, Types.OTHER)
			.param("expires", (lease != null) ? timestamp(lease.expiresAt()) : null, Types.TIMESTAMP_WITH_TIMEZONE)
			.update();
	}

	@Override
	public boolean deleteIfUnchanged(long eventId, UUID token, int notifications) {
		return this.jdbc.sql("""
				delete from storyline_linking_queue
				where event_id = :event and lease_token = :token and notifications = :notifications
				""")
			.param("event", eventId)
			.param("token", token)
			.param("notifications", notifications)
			.update() == 1;
	}

	@Override
	public boolean releaseIfLeased(long eventId, UUID token) {
		return this.jdbc.sql("""
				update storyline_linking_queue set lease_token = null, lease_expires_at = null
				where event_id = :event and lease_token = :token
				""").param("event", eventId).param("token", token).update() == 1;
	}

	@Override
	public OptionalInt recordFailureIfLeased(long eventId, UUID token) {
		return this.jdbc.sql("""
				update storyline_linking_queue
				set failures = failures + 1, lease_token = null, lease_expires_at = null
				where event_id = :event and lease_token = :token
				returning failures
				""")
			.param("event", eventId)
			.param("token", token)
			.query(Integer.class)
			.optional()
			.map(OptionalInt::of)
			.orElseGet(OptionalInt::empty);
	}

	@Override
	public void delete(long eventId) {
		this.jdbc.sql("delete from storyline_linking_queue where event_id = :event").param("event", eventId).update();
	}

	private static PendingEvent row(ResultSet rs, int row) throws SQLException {
		UUID token = rs.getObject("lease_token", UUID.class);
		OffsetDateTime order = rs.getObject("order_at", OffsetDateTime.class);
		return new PendingEvent(rs.getLong("event_id"), (order != null) ? order.toInstant() : null,
				rs.getInt("notifications"), rs.getInt("failures"),
				(token != null)
						? new LinkingLease(token, rs.getObject("lease_expires_at", OffsetDateTime.class).toInstant())
						: null);
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
