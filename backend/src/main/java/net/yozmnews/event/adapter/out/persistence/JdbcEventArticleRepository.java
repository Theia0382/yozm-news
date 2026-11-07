package net.yozmnews.event.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.assignment.DecidedBy;
import net.yozmnews.event.domain.assignment.StoredCandidate;
import net.yozmnews.event.domain.event.EventArticle;
import net.yozmnews.event.domain.event.EventArticleRepository;
import net.yozmnews.event.domain.event.PublishedSpan;
import net.yozmnews.event.domain.event.RejudgeTarget;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link EventArticleRepository} 의 JdbcClient 구현 — docs/specs/event.md §4.
 * <p>
 * 배정은 {@code INSERT … ON CONFLICT DO NOTHING} 으로 쓰고, 썼을 때만 후보를 쓴다. 재판정 대상은 기준 ID 가 다른 배정을
 * {@code criteria_id < :c or criteria_id > :c} 로 찾아 기준 인덱스를 쓴다. 저장 후보의 합쳐진 사건은 {@code merged_into} 로 읽는다 —
 * 같은 사건이 둘이 되면 모으기는 {@link RejudgeTarget} 가 한다. 잠금은 배정 행에만 건다.
 */
@Repository
class JdbcEventArticleRepository implements EventArticleRepository {

	private static final String COLUMNS = """
			article_id, event_id, published_at, score, decided_by, opened, criteria_id, assigned_at
			""";

	private final JdbcClient jdbc;

	JdbcEventArticleRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean exists(ArticleId articleId) {
		return this.jdbc.sql("select exists (select 1 from event_articles where article_id = :article)")
			.param("article", articleId.value())
			.query(Boolean.class)
			.single();
	}

	@Override
	public boolean insertIfAbsent(EventArticle row, List<StoredCandidate> stored) {
		boolean inserted = this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, score, decided_by, opened, criteria_id,
				                            assigned_at)
				values (:article, :event, :published, :score, :by, :opened, :criteria, :assigned)
				on conflict (article_id) do nothing
				""")
			.param("article", row.articleId().value())
			.param("event", row.eventId())
			.param("published", timestamp(row.publishedAt()))
			.param("score", row.score(), Types.DOUBLE)
			.param("by", row.decidedBy().name())
			.param("opened", row.opened())
			.param("criteria", row.criteriaId())
			.param("assigned", timestamp(row.assignedAt()))
			.update() == 1;
		if (inserted) {
			for (StoredCandidate candidate : stored) {
				this.jdbc.sql("""
						insert into event_article_candidates (article_id, event_id, score, probability)
						values (:article, :event, :score, :probability)
						""")
					.param("article", row.articleId().value())
					.param("event", candidate.eventId())
					.param("score", candidate.score())
					.param("probability", candidate.probability(), Types.DOUBLE)
					.update();
			}
		}
		return inserted;
	}

	@Override
	public List<RejudgeTarget> lockRejudgeTargets(long criteriaId, int limit) {
		List<EventArticle> assignments = this.jdbc.sql("select " + COLUMNS + """
				from event_articles
				where criteria_id < :criteria or criteria_id > :criteria
				order by article_id
				limit :limit
				for update skip locked
				""").param("criteria", criteriaId).param("limit", limit).query(JdbcEventArticleRepository::row).list();
		if (assignments.isEmpty()) {
			return List.of();
		}
		Long[] articles = assignments.stream().map(a -> a.articleId().value()).toArray(Long[]::new);
		Map<Long, List<StoredCandidate>> stored = new HashMap<>();
		this.jdbc.sql("""
				select c.article_id, coalesce(e.merged_into, e.id) as event_id, c.score, c.probability
				from event_article_candidates c
				join events e on e.id = c.event_id
				where c.article_id = any(:articles)
				order by c.article_id, c.event_id
				""").param("articles", articles).query((ResultSet rs) -> {
					stored.computeIfAbsent(rs.getLong("article_id"), id -> new ArrayList<>())
						.add(new StoredCandidate(rs.getLong("event_id"), rs.getDouble("score"),
								rs.getObject("probability", Double.class)));
				});
		return assignments.stream()
			.map(assignment -> new RejudgeTarget(assignment,
					stored.getOrDefault(assignment.articleId().value(), List.of())))
			.toList();
	}

	@Override
	public void reassign(ArticleId articleId, long eventId, @Nullable Double score, DecidedBy by, boolean opened,
			long criteriaId, Instant now) {
		this.jdbc.sql("""
				update event_articles set event_id = :event, score = :score, decided_by = :by, opened = :opened,
				                          criteria_id = :criteria, assigned_at = :now
				where article_id = :article
				""")
			.param("article", articleId.value())
			.param("event", eventId)
			.param("score", score, Types.DOUBLE)
			.param("by", by.name())
			.param("opened", opened)
			.param("criteria", criteriaId)
			.param("now", timestamp(now))
			.update();
	}

	@Override
	public void keep(ArticleId articleId, long criteriaId) {
		this.jdbc.sql("update event_articles set criteria_id = :criteria where article_id = :article")
			.param("article", articleId.value())
			.param("criteria", criteriaId)
			.update();
	}

	@Override
	public int keepClosed(long criteriaId, int limit) {
		return this.jdbc.sql("""
				update event_articles set criteria_id = :criteria
				where article_id in (
				    select a.article_id from event_articles a
				    join events e on e.id = a.event_id
				    where e.status = 'CLOSED' and (a.criteria_id < :criteria or a.criteria_id > :criteria)
				    order by a.article_id
				    limit :limit
				    for update of a skip locked)
				""").param("criteria", criteriaId).param("limit", limit).update();
	}

	@Override
	public List<EventArticle> lockByArticles(Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc
			.sql("select " + COLUMNS + " from event_articles where article_id = any(:ids) order by article_id for update")
			.param("ids", ids(articleIds))
			.query(JdbcEventArticleRepository::row)
			.list();
	}

	@Override
	public List<EventArticle> lockByEvents(Collection<Long> eventIds) {
		if (eventIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc
			.sql("select " + COLUMNS + " from event_articles where event_id = any(:ids) order by article_id for update")
			.param("ids", eventIds.toArray(Long[]::new))
			.query(JdbcEventArticleRepository::row)
			.list();
	}

	@Override
	public void moveAll(long fromEventId, long toEventId) {
		this.jdbc.sql("update event_articles set event_id = :to where event_id = :from")
			.param("from", fromEventId)
			.param("to", toEventId)
			.update();
	}

	@Override
	public Optional<PublishedSpan> publishedSpan(long eventId) {
		return this.jdbc.sql("""
				select min(published_at) as first, max(published_at) as last from event_articles where event_id = :event
				having count(*) > 0
				""")
			.param("event", eventId)
			.query((rs, row) -> new PublishedSpan(instant(rs, "first"), instant(rs, "last")))
			.optional();
	}

	@Override
	public void deleteByArticles(Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return;
		}
		Long[] ids = ids(articleIds);
		this.jdbc.sql("delete from event_article_candidates where article_id = any(:ids)").param("ids", ids).update();
		this.jdbc.sql("delete from event_articles where article_id = any(:ids)").param("ids", ids).update();
	}

	private static Long[] ids(Collection<ArticleId> articleIds) {
		return articleIds.stream().map(ArticleId::value).toArray(Long[]::new);
	}

	private static EventArticle row(ResultSet rs, int row) throws SQLException {
		return new EventArticle(new ArticleId(rs.getLong("article_id")), rs.getLong("event_id"),
				instant(rs, "published_at"), rs.getObject("score", Double.class),
				DecidedBy.valueOf(rs.getString("decided_by")), rs.getBoolean("opened"), rs.getLong("criteria_id"),
				instant(rs, "assigned_at"));
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

}
