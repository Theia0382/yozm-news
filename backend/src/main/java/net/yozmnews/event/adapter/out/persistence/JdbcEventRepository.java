package net.yozmnews.event.adapter.out.persistence;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventStatus;
import net.yozmnews.event.domain.assignment.CandidateScoring;
import net.yozmnews.event.domain.assignment.EventSnapshot;
import net.yozmnews.event.domain.event.EventRepository;
import net.yozmnews.event.domain.event.LockedEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link EventRepository} 의 JdbcClient 구현 — docs/specs/event.md §4.
 * <p>
 * 중심은 차원 없는 {@code halfvec} 이고 {@link VectorText} 의 텍스트 표현으로 쓰고 읽는다. 코사인 유사도는 {@code 1 − (중심 <=> 벡터)}
 * 를 열린 사건마다 센다(인덱스를 쓰지 않는 정확 검색). 사건 핵심 말의 상위 수는 {@link CandidateScoring#EVENT_KEY_TERMS} 를 매개변수로
 * 넘긴다. 말 순은 바이트 순({@code COLLATE "C"})이라 DB 의 정렬 규칙과 무관하다.
 */
@Repository
class JdbcEventRepository implements EventRepository {

	private static final String EVENT_COLUMNS = """
			id, status, model_version, centroid::text as centroid, article_count, first_article_at, last_article_at
			""";

	private final JdbcClient jdbc;

	JdbcEventRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<EventSnapshot> candidates(String modelVersion, float[] vector, Instant from, Instant to) {
		return this.jdbc.sql("""
				select e.id, 1 - (e.centroid <=> cast(:vector as halfvec)) as cosine, e.first_article_at, e.last_article_at,
				       array(select k.term from event_key_terms k where k.event_id = e.id
				             order by k.article_count desc, k.term collate "C" limit :terms) as key_terms
				from events e
				where e.status = 'OPEN' and e.model_version = :model
				  and e.last_article_at >= :from and e.first_article_at <= :to
				order by e.id
				""")
			.param("vector", VectorText.of(vector))
			.param("terms", CandidateScoring.EVENT_KEY_TERMS)
			.param("model", modelVersion)
			.param("from", timestamp(from))
			.param("to", timestamp(to))
			.query((rs, row) -> new EventSnapshot(rs.getLong("id"), rs.getDouble("cosine"), strings(rs.getArray("key_terms")),
					instant(rs, "first_article_at"), instant(rs, "last_article_at")))
			.list();
	}

	@Override
	public List<ArticleId> describers(long eventId, String modelVersion, int limit) {
		return this.jdbc.sql("""
				select a.article_id from event_articles a
				join events e on e.id = a.event_id
				join article_match_features f on f.article_id = a.article_id and f.model_version = :model
				where a.event_id = :event
				order by cast(f.embedding as halfvec) <=> e.centroid, a.article_id
				limit :limit
				""")
			.param("event", eventId)
			.param("model", modelVersion)
			.param("limit", limit)
			.query((rs, row) -> new ArticleId(rs.getLong("article_id")))
			.list();
	}

	@Override
	public Optional<LockedEvent> lock(long eventId) {
		return this.jdbc.sql("select " + EVENT_COLUMNS + " from events where id = :id for update")
			.param("id", eventId)
			.query(JdbcEventRepository::event)
			.optional();
	}

	@Override
	public List<LockedEvent> lockAll(Collection<Long> eventIds) {
		if (eventIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc.sql("select " + EVENT_COLUMNS + " from events where id = any(:ids) order by id for update")
			.param("ids", eventIds.toArray(Long[]::new))
			.query(JdbcEventRepository::event)
			.list();
	}

	@Override
	public long open(String modelVersion, float[] centroid, Instant publishedAt, List<String> keyTerms, Instant now) {
		long id = this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at)
				values ('OPEN', :model, cast(:centroid as halfvec), 1, :published, :published, :now)
				returning id
				""")
			.param("model", modelVersion)
			.param("centroid", VectorText.of(centroid))
			.param("published", timestamp(publishedAt))
			.param("now", timestamp(now))
			.query(Long.class)
			.single();
		// 기사 하나의 핵심 말이다 — 같은 말은 한 번 센다
		addTerms(id, List.copyOf(new LinkedHashSet<>(keyTerms)));
		return id;
	}

	@Override
	public void update(LockedEvent next, List<String> addedTerms, List<String> removedTerms) {
		this.jdbc.sql("""
				update events set centroid = cast(:centroid as halfvec), article_count = :count,
				                  first_article_at = :first, last_article_at = :last
				where id = :id
				""")
			.param("id", next.id())
			.param("centroid", VectorText.of(next.centroid()))
			.param("count", next.articleCount())
			.param("first", timestamp(next.firstArticleAt()))
			.param("last", timestamp(next.lastArticleAt()))
			.update();
		addTerms(next.id(), addedTerms);
		removeTerms(next.id(), removedTerms);
	}

	@Override
	public void delete(long eventId) {
		this.jdbc.sql("delete from event_article_candidates where event_id = :id").param("id", eventId).update();
		this.jdbc.sql("delete from events where id = :id").param("id", eventId).update();
	}

	private void addTerms(long eventId, List<String> terms) {
		if (terms.isEmpty()) {
			return;
		}
		this.jdbc.sql("""
				insert into event_key_terms (event_id, term, article_count)
				select :id, t.term, count(*) from unnest(cast(:terms as text[])) as t(term) group by t.term
				on conflict (event_id, term) do update set article_count = event_key_terms.article_count + excluded.article_count
				""").param("id", eventId).param("terms", terms.toArray(String[]::new)).update();
	}

	private void removeTerms(long eventId, List<String> terms) {
		if (terms.isEmpty()) {
			return;
		}
		String[] removed = terms.toArray(String[]::new);
		// 0 이하가 될 말을 먼저 지운다 — 남는 말만 줄여 CHECK (article_count >= 1) 를 어기지 않는다
		this.jdbc.sql("""
				delete from event_key_terms k
				using (select t.term, count(*) as n from unnest(cast(:terms as text[])) as t(term) group by t.term) c
				where k.event_id = :id and k.term = c.term and k.article_count <= c.n
				""").param("id", eventId).param("terms", removed).update();
		this.jdbc.sql("""
				update event_key_terms k set article_count = k.article_count - c.n
				from (select t.term, count(*) as n from unnest(cast(:terms as text[])) as t(term) group by t.term) c
				where k.event_id = :id and k.term = c.term
				""").param("id", eventId).param("terms", removed).update();
	}

	private static LockedEvent event(ResultSet rs, int row) throws SQLException {
		return new LockedEvent(rs.getLong("id"), EventStatus.valueOf(rs.getString("status")),
				rs.getString("model_version"), VectorText.parse(rs.getString("centroid")), rs.getInt("article_count"),
				instant(rs, "first_article_at"), instant(rs, "last_article_at"));
	}

	private static List<String> strings(Array array) throws SQLException {
		return List.of((String[]) array.getArray());
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

}
