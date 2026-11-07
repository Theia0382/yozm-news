package net.yozmnews.event.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.DecidedBy;
import net.yozmnews.event.EventArticleView;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventStatus;
import net.yozmnews.event.EventView;
import net.yozmnews.event.domain.event.EventQueryRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link EventQueryRepository} 의 JdbcClient 구현 — docs/specs/event.md §4.
 * <p>
 * 대표 기사의 정렬은 {@link CentroidOrder#ORDER} 다 — 표현은 사건 모델 버전의 행만 왼쪽 조인한다. 기간은
 * {@code event_articles.published_at} 범위로 고른다(인덱스 {@code event_articles_published_idx}).
 */
@Repository
class JdbcEventQueryRepository implements EventQueryRepository {

	private static final String EVENT_COLUMNS = """
			e.id, e.status, e.article_count, e.first_article_at, e.last_article_at, e.created_at
			""";

	private static final String IN_PERIOD = "a.published_at >= :from and a.published_at < :to";

	private final JdbcClient jdbc;

	JdbcEventQueryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<EventView> find(long eventId) {
		return this.jdbc.sql("select " + EVENT_COLUMNS + " from events e where e.id = :id")
			.param("id", eventId)
			.query(JdbcEventQueryRepository::event)
			.optional();
	}

	@Override
	public List<EventArticleView> articles(long eventId) {
		return this.jdbc.sql("""
				select a.article_id, a.published_at, a.score, a.decided_by, a.assigned_at from event_articles a
				where a.event_id = :event
				order by a.published_at, a.article_id
				""").param("event", eventId).query((rs, row) -> new EventArticleView(new ArticleId(rs.getLong("article_id")),
				instant(rs, "published_at"), rs.getObject("score", Double.class), DecidedBy.valueOf(rs.getString("decided_by")),
				instant(rs, "assigned_at"))).list();
	}

	@Override
	public Map<ArticleId, Long> eventsOf(Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return Map.of();
		}
		Map<ArticleId, Long> events = new LinkedHashMap<>();
		this.jdbc.sql("select article_id, event_id from event_articles where article_id = any(:ids) order by article_id")
			.param("ids", articleIds.stream().map(ArticleId::value).toArray(Long[]::new))
			.query((ResultSet rs) -> {
				events.put(new ArticleId(rs.getLong("article_id")), rs.getLong("event_id"));
			});
		return Collections.unmodifiableMap(events);
	}

	@Override
	public Map<Long, ArticleId> representatives(Map<Long, ? extends Collection<ArticleId>> among) {
		List<Long> events = new ArrayList<>();
		List<Long> articles = new ArrayList<>();
		among.forEach((event, ids) -> ids.forEach(article -> {
			events.add(event);
			articles.add(article.value());
		}));
		if (events.isEmpty()) {
			return Map.of();
		}
		return firstPerEvent(this.jdbc.sql("""
				select distinct on (a.event_id) a.event_id, a.article_id
				from unnest(cast(:events as bigint[]), cast(:articles as bigint[])) as g(event_id, article_id)
				join event_articles a on a.article_id = g.article_id and a.event_id = g.event_id
				join events e on e.id = a.event_id
				left join article_match_features f on f.article_id = a.article_id and f.model_version = e.model_version
				order by a.event_id, %s
				""".formatted(CentroidOrder.ORDER))
			.param("events", events.toArray(Long[]::new))
			.param("articles", articles.toArray(Long[]::new)));
	}

	@Override
	public Map<Long, ArticleId> representativesOfAll(Collection<Long> eventIds) {
		if (eventIds.isEmpty()) {
			return Map.of();
		}
		return firstPerEvent(this.jdbc.sql("""
				select distinct on (a.event_id) a.event_id, a.article_id
				from event_articles a
				join events e on e.id = a.event_id
				left join article_match_features f on f.article_id = a.article_id and f.model_version = e.model_version
				where a.event_id = any(:events)
				order by a.event_id, %s
				""".formatted(CentroidOrder.ORDER)).param("events", eventIds.toArray(Long[]::new)));
	}

	@Override
	public Map<Long, List<ArticleId>> membersIn(Instant from, Instant to) {
		return members(this.jdbc.sql("""
				select a.event_id, a.article_id from event_articles a
				where %s
				order by a.event_id, a.published_at, a.article_id
				""".formatted(IN_PERIOD)).param("from", timestamp(from)).param("to", timestamp(to)));
	}

	@Override
	public Map<Long, List<ArticleId>> membersIn(Collection<Long> eventIds, Instant from, Instant to) {
		if (eventIds.isEmpty()) {
			return Map.of();
		}
		return members(this.jdbc.sql("""
				select a.event_id, a.article_id from event_articles a
				where a.event_id = any(:events) and %s
				order by a.event_id, a.published_at, a.article_id
				""".formatted(IN_PERIOD))
			.param("events", eventIds.toArray(Long[]::new))
			.param("from", timestamp(from))
			.param("to", timestamp(to)));
	}

	@Override
	public List<EventView> page(Instant from, Instant to, @Nullable Instant afterLast, @Nullable Long afterId, int limit) {
		String after = afterLast == null ? "" : "and (e.last_article_at, e.id) < (:afterLast, :afterId)";
		JdbcClient.StatementSpec statement = this.jdbc.sql("""
				select %s from events e
				where exists (select 1 from event_articles a where a.event_id = e.id and %s)
				  %s
				order by e.last_article_at desc, e.id desc
				limit :limit
				""".formatted(EVENT_COLUMNS, IN_PERIOD, after))
			.param("from", timestamp(from))
			.param("to", timestamp(to))
			.param("limit", limit);
		if (afterLast != null) {
			statement = statement.param("afterLast", timestamp(afterLast)).param("afterId", afterId);
		}
		return statement.query(JdbcEventQueryRepository::event).list();
	}

	private static Map<Long, ArticleId> firstPerEvent(JdbcClient.StatementSpec statement) {
		Map<Long, ArticleId> representatives = new LinkedHashMap<>();
		statement.query((ResultSet rs) -> {
			representatives.put(rs.getLong("event_id"), new ArticleId(rs.getLong("article_id")));
		});
		return Collections.unmodifiableMap(representatives);
	}

	private static Map<Long, List<ArticleId>> members(JdbcClient.StatementSpec statement) {
		Map<Long, List<ArticleId>> members = new LinkedHashMap<>();
		statement.query((ResultSet rs) -> {
			members.computeIfAbsent(rs.getLong("event_id"), event -> new ArrayList<>())
				.add(new ArticleId(rs.getLong("article_id")));
		});
		Map<Long, List<ArticleId>> result = new LinkedHashMap<>();
		members.forEach((event, articles) -> result.put(event, List.copyOf(articles)));
		return Collections.unmodifiableMap(result);
	}

	private static EventView event(ResultSet rs, int row) throws SQLException {
		return new EventView(new EventId(rs.getLong("id")), EventStatus.valueOf(rs.getString("status")),
				rs.getInt("article_count"), instant(rs, "first_article_at"), instant(rs, "last_article_at"),
				instant(rs, "created_at"));
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

}
