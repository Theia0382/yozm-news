package net.yozmnews.storyline.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import net.yozmnews.storyline.domain.queue.LinkingLease;
import net.yozmnews.storyline.domain.relation.PendingStoryline;
import net.yozmnews.storyline.domain.relation.RelationQueueRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link RelationQueueRepository} 의 JdbcClient 구현 — docs/specs/storyline.md §4.
 * <p>
 * 넣기는 {@code INSERT … ON CONFLICT (storyline_id) DO UPDATE} 로 다시 알림 수를 올린다. 잠금은 스토리라인 ID 순
 * {@code FOR UPDATE SKIP LOCKED}, 점유한 회차의 반영은 점유 토큰(지우기는 다시 알림 수도)을 조건으로 한 갱신 · 삭제다.
 */
@Repository
class JdbcRelationQueueRepository implements RelationQueueRepository {

	private final JdbcClient jdbc;

	JdbcRelationQueueRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void enqueue(long storylineId, Instant now) {
		this.jdbc.sql("""
				insert into storyline_relation_queue (storyline_id, enqueued_at) values (:storyline, :now)
				on conflict (storyline_id) do update set notifications = storyline_relation_queue.notifications + 1
				""").param("storyline", storylineId).param("now", timestamp(now)).update();
	}

	@Override
	public List<PendingStoryline> lockDue(Instant now, int limit) {
		return this.jdbc.sql("""
				select storyline_id, notifications, failures, lease_token, lease_expires_at
				from storyline_relation_queue
				where lease_token is null or lease_expires_at <= :now
				order by storyline_id
				limit :limit
				for update skip locked
				""").param("now", timestamp(now)).param("limit", limit).query(JdbcRelationQueueRepository::row).list();
	}

	@Override
	public void saveLocked(PendingStoryline next) {
		LinkingLease lease = next.lease();
		this.jdbc.sql("""
				update storyline_relation_queue set lease_token = :token, lease_expires_at = :expires
				where storyline_id = :storyline
				""")
			.param("storyline", next.storylineId())
			.param("token", (lease != null) ? lease.token() : null, Types.OTHER)
			.param("expires", (lease != null) ? timestamp(lease.expiresAt()) : null, Types.TIMESTAMP_WITH_TIMEZONE)
			.update();
	}

	@Override
	public boolean deleteIfUnchanged(long storylineId, UUID token, int notifications) {
		return this.jdbc.sql("""
				delete from storyline_relation_queue
				where storyline_id = :storyline and lease_token = :token and notifications = :notifications
				""")
			.param("storyline", storylineId)
			.param("token", token)
			.param("notifications", notifications)
			.update() == 1;
	}

	@Override
	public boolean releaseIfLeased(long storylineId, UUID token) {
		return this.jdbc.sql("""
				update storyline_relation_queue set lease_token = null, lease_expires_at = null
				where storyline_id = :storyline and lease_token = :token
				""").param("storyline", storylineId).param("token", token).update() == 1;
	}

	@Override
	public OptionalInt recordFailureIfLeased(long storylineId, UUID token) {
		return this.jdbc.sql("""
				update storyline_relation_queue
				set failures = failures + 1, lease_token = null, lease_expires_at = null
				where storyline_id = :storyline and lease_token = :token
				returning failures
				""")
			.param("storyline", storylineId)
			.param("token", token)
			.query(Integer.class)
			.optional()
			.map(OptionalInt::of)
			.orElseGet(OptionalInt::empty);
	}

	@Override
	public void delete(long storylineId) {
		this.jdbc.sql("delete from storyline_relation_queue where storyline_id = :storyline")
			.param("storyline", storylineId)
			.update();
	}

	private static PendingStoryline row(ResultSet rs, int row) throws SQLException {
		UUID token = rs.getObject("lease_token", UUID.class);
		return new PendingStoryline(rs.getLong("storyline_id"), rs.getInt("notifications"), rs.getInt("failures"),
				(token != null)
						? new LinkingLease(token, rs.getObject("lease_expires_at", OffsetDateTime.class).toInstant())
						: null);
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
