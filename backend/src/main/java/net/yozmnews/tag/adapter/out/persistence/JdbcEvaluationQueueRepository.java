package net.yozmnews.tag.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.yozmnews.article.ArticleId;
import net.yozmnews.tag.EvaluationQueueCounts;
import net.yozmnews.tag.domain.queue.EvaluationLease;
import net.yozmnews.tag.domain.queue.EvaluationQueueRepository;
import net.yozmnews.tag.domain.queue.EvaluationSchedule;
import net.yozmnews.tag.domain.queue.PendingEvaluation;
import net.yozmnews.tag.domain.queue.QueueFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link EvaluationQueueRepository} 의 JdbcClient 구현 — docs/specs/tag.md §4.
 * <p>
 * 넣기는 {@code INSERT … ON CONFLICT DO NOTHING}, 재평가는 실패 행만 되돌리는 {@code ON CONFLICT … DO UPDATE … WHERE}, 잠금은
 * {@code FOR UPDATE SKIP LOCKED}, 점유한 회차의 반영은 점유 토큰을 조건으로 한 갱신 · 삭제다. 평가 가능 시각의 정착 시간은
 * {@link EvaluationSchedule} 이 정한다 — SQL 에 값을 두지 않는다.
 */
@Repository
class JdbcEvaluationQueueRepository implements EvaluationQueueRepository {

	private static final String COLUMNS = "article_id, eligible_at, attempts, lease_token, lease_expires_at, failure, last_error";

	/** 대기 행 하나를 고쳐 쓰는 열 — 기사 ID 는 조건이다 */
	private static final String ASSIGNMENTS = """
			eligible_at = :eligible, attempts = :attempts, lease_token = :leaseToken, \
			lease_expires_at = :leaseExpiresAt, failure = :failure, last_error = :lastError""";

	private final JdbcClient jdbc;

	JdbcEvaluationQueueRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void enqueue(ArticleId articleId, Instant now) {
		this.jdbc.sql("""
				insert into evaluation_queue (article_id, enqueued_at, eligible_at) values (:article, :now, :eligible)
				on conflict (article_id) do nothing
				""")
			.param("article", articleId.value())
			.param("now", timestamp(now))
			.param("eligible", timestamp(EvaluationSchedule.eligibleAfterEnqueue(now)))
			.update();
	}

	@Override
	public int requeue(Collection<ArticleId> articleIds, Instant now) {
		if (articleIds.isEmpty()) {
			return 0;
		}
		return this.jdbc.sql("""
				insert into evaluation_queue (article_id, enqueued_at, eligible_at)
				select id, :now, :eligible from unnest(cast(:ids as bigint[])) as requeued(id)
				on conflict (article_id) do update
				set eligible_at = excluded.eligible_at, attempts = 0, failure = null, last_error = null
				where evaluation_queue.failure is not null
				""")
			// 한 문장이 같은 행을 두 번 갱신할 수 없다 — 겹친 ID 는 하나로
			.param("ids", articleIds.stream().map(ArticleId::value).distinct().toArray(Long[]::new))
			.param("now", timestamp(now))
			.param("eligible", timestamp(EvaluationSchedule.eligibleAfterRequeue(now)))
			.update();
	}

	@Override
	public List<PendingEvaluation> lockDue(Instant now, int limit) {
		return this.jdbc.sql("select " + COLUMNS + """
				 from evaluation_queue
				where failure is null and eligible_at <= :now and (lease_token is null or lease_expires_at <= :now)
				order by eligible_at, article_id
				limit :limit
				for update skip locked
				""").param("now", timestamp(now)).param("limit", limit).query(JdbcEvaluationQueueRepository::row).list();
	}

	@Override
	public void saveLocked(PendingEvaluation next) {
		this.jdbc.sql("update evaluation_queue set " + ASSIGNMENTS + " where article_id = :article")
			.params(values(next))
			.update();
	}

	@Override
	public boolean replaceIfLeased(UUID token, PendingEvaluation next) {
		Map<String, Object> values = values(next);
		values.put("token", token);
		return this.jdbc.sql("update evaluation_queue set " + ASSIGNMENTS
				+ " where article_id = :article and lease_token = :token").params(values).update() == 1;
	}

	@Override
	public boolean deleteIfLeased(ArticleId articleId, UUID token) {
		return this.jdbc.sql("delete from evaluation_queue where article_id = :article and lease_token = :token")
			.param("article", articleId.value())
			.param("token", token)
			.update() == 1;
	}

	@Override
	public void delete(Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return;
		}
		this.jdbc.sql("delete from evaluation_queue where article_id = any(:ids)")
			.param("ids", articleIds.stream().map(ArticleId::value).toArray(Long[]::new))
			.update();
	}

	@Override
	public EvaluationQueueCounts counts() {
		return this.jdbc.sql("""
				select count(*) filter (where failure is null) as waiting,
				    count(*) filter (where failure = 'ARTICLE_REJECTED') as rejected,
				    count(*) filter (where failure = 'RETRIES_EXHAUSTED') as exhausted
				from evaluation_queue
				""")
			.query((rs, row) -> new EvaluationQueueCounts(rs.getLong("waiting"), rs.getLong("rejected"),
					rs.getLong("exhausted")))
			.single();
	}

	private static Map<String, Object> values(PendingEvaluation pending) {
		EvaluationLease lease = pending.lease();
		QueueFailure failure = pending.failure();
		Map<String, Object> values = new HashMap<>();
		values.put("article", pending.articleId().value());
		values.put("eligible", timestamp(pending.eligibleAt()));
		values.put("attempts", pending.attempts());
		values.put("leaseToken", (lease != null) ? lease.token() : null);
		values.put("leaseExpiresAt", (lease != null) ? timestamp(lease.expiresAt()) : null);
		values.put("failure", (failure != null) ? failure.name() : null);
		values.put("lastError", pending.lastError());
		return values;
	}

	private static PendingEvaluation row(ResultSet rs, int row) throws SQLException {
		UUID token = rs.getObject("lease_token", UUID.class);
		String failure = rs.getString("failure");
		return new PendingEvaluation(new ArticleId(rs.getLong("article_id")), instant(rs, "eligible_at"),
				rs.getInt("attempts"), (token != null) ? new EvaluationLease(token, instant(rs, "lease_expires_at")) : null,
				(failure != null) ? QueueFailure.valueOf(failure) : null, rs.getString("last_error"));
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

}
