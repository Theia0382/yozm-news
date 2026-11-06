package net.yozmnews.event.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.assignment.PendingAssignment;
import net.yozmnews.event.domain.intake.IntakeLease;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link AssignmentQueueRepository} 의 JdbcClient 구현 — docs/specs/event.md §4.
 * <p>
 * 넣기는 {@code INSERT … ON CONFLICT DO NOTHING}, 잠금은 {@code FOR UPDATE SKIP LOCKED}, 점유한 회차의 반영은 점유 토큰을 조건으로 한
 * 갱신 · 삭제다.
 */
@Repository
class JdbcAssignmentQueueRepository implements AssignmentQueueRepository {

	private final JdbcClient jdbc;

	JdbcAssignmentQueueRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void enqueue(ArticleId articleId, Instant now) {
		this.jdbc.sql("""
				insert into event_assignment_queue (article_id, enqueued_at, available_at) values (:article, :now, :now)
				on conflict (article_id) do nothing
				""").param("article", articleId.value()).param("now", timestamp(now)).update();
	}

	@Override
	public List<PendingAssignment> lockDue(Instant now, int limit) {
		return this.jdbc.sql("""
				select article_id, enqueued_at, attempts, available_at, lease_token, lease_expires_at
				from event_assignment_queue
				where available_at <= :now and (lease_token is null or lease_expires_at <= :now)
				order by available_at, article_id
				limit :limit
				for update skip locked
				""").param("now", timestamp(now)).param("limit", limit).query(JdbcAssignmentQueueRepository::row).list();
	}

	@Override
	public void saveLocked(PendingAssignment next) {
		IntakeLease lease = next.lease();
		this.jdbc.sql("""
				update event_assignment_queue
				set attempts = :attempts, available_at = :available, lease_token = :token, lease_expires_at = :expires
				where article_id = :article
				""")
			.param("article", next.articleId().value())
			.param("attempts", next.attempts())
			.param("available", timestamp(next.availableAt()))
			.param("token", (lease != null) ? lease.token() : null, Types.OTHER)
			.param("expires", (lease != null) ? timestamp(lease.expiresAt()) : null, Types.TIMESTAMP_WITH_TIMEZONE)
			.update();
	}

	@Override
	public boolean deleteIfLeased(ArticleId articleId, UUID token) {
		return this.jdbc.sql("delete from event_assignment_queue where article_id = :article and lease_token = :token")
			.param("article", articleId.value())
			.param("token", token)
			.update() == 1;
	}

	@Override
	public boolean releaseIfLeased(ArticleId articleId, UUID token) {
		return this.jdbc.sql("""
				update event_assignment_queue set lease_token = null, lease_expires_at = null
				where article_id = :article and lease_token = :token
				""").param("article", articleId.value()).param("token", token).update() == 1;
	}

	@Override
	public boolean retryIfLeased(ArticleId articleId, UUID token, int attempts, Instant availableAt) {
		return this.jdbc.sql("""
				update event_assignment_queue
				set attempts = :attempts, available_at = :available, lease_token = null, lease_expires_at = null
				where article_id = :article and lease_token = :token
				""")
			.param("article", articleId.value())
			.param("token", token)
			.param("attempts", attempts)
			.param("available", timestamp(availableAt))
			.update() == 1;
	}

	@Override
	public void delete(Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return;
		}
		this.jdbc.sql("delete from event_assignment_queue where article_id = any(:ids)")
			.param("ids", articleIds.stream().map(ArticleId::value).toArray(Long[]::new))
			.update();
	}

	private static PendingAssignment row(ResultSet rs, int row) throws SQLException {
		UUID token = rs.getObject("lease_token", UUID.class);
		return new PendingAssignment(new ArticleId(rs.getLong("article_id")), instant(rs, "enqueued_at"),
				rs.getInt("attempts"), instant(rs, "available_at"),
				(token != null) ? new IntakeLease(token, instant(rs, "lease_expires_at")) : null);
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

}
