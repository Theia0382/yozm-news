package net.yozmnews.event.adapter.out.persistence;

import java.sql.Array;
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
import net.yozmnews.event.EventKeyTerm;
import net.yozmnews.event.EventNeighbor;
import net.yozmnews.event.EventProfile;
import net.yozmnews.event.EventStatus;
import net.yozmnews.event.EventView;
import net.yozmnews.event.JudgementUse;
import net.yozmnews.event.MergePair;
import net.yozmnews.event.MergedEvent;
import net.yozmnews.event.domain.event.EventQueryRepository;
import net.yozmnews.platform.VectorText;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link EventQueryRepository} 의 JdbcClient 구현 — docs/specs/event.md §4.
 * <p>
 * 대표 기사의 정렬은 {@link CentroidOrder#ORDER} 다 — 표현은 사건 모델 버전의 행만 왼쪽 조인한다. 기간은
 * {@code event_articles.published_at} 범위로 고른다(인덱스 {@code event_articles_published_idx}). 이웃 사건은 기준 사건과 같은 모델
 * 버전의 합쳐지지 않은 사건마다 코사인을 센다(인덱스 없음) — 거리가 NaN(영 벡터 중심)인 행은 뺀다. 이웃 · 채우기 대상의 시각 조건은
 * {@code events_unmerged_idx} 로 거른다.
 */
@Repository
class JdbcEventQueryRepository implements EventQueryRepository {

	private static final String EVENT_COLUMNS = """
			e.id, e.status, e.article_count, e.first_article_at, e.last_article_at, e.created_at
			""";

	/** 사건 {@code e} 의 핵심 말과 그 기사 수 상위 {@code :terms} 개 — 두 배열의 순서가 같다 */
	private static final String TOP_TERMS_WITH_COUNTS = """
			array(select k.term from event_key_terms k where k.event_id = e.id
			      order by k.article_count desc, k.term collate "C" limit :terms) as key_terms,
			array(select k.article_count from event_key_terms k where k.event_id = e.id
			      order by k.article_count desc, k.term collate "C" limit :terms) as key_counts
			""";

	private static final String IN_PERIOD = "a.published_at >= :from and a.published_at < :to";

	private final JdbcClient jdbc;

	JdbcEventQueryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<EventView> find(long eventId) {
		// 합쳐진 사건이면 남는 사건 — 가리킴은 평평해 한 번이면 된다
		return this.jdbc.sql("select " + EVENT_COLUMNS
				+ " from events e where e.id = coalesce((select m.merged_into from events m where m.id = :id), :id)")
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
		// 닫을 때 남긴 대표가 있으면 그것 — 닫힌 사건은 표현을 지워 중심에 가까운 순으로 다시 고를 수 없다
		return firstPerEvent(this.jdbc.sql("""
				select e.id as event_id, e.representative_article_id as article_id from events e
				where e.id = any(:events) and e.representative_article_id is not null
				union all
				(select distinct on (a.event_id) a.event_id, a.article_id
				 from event_articles a
				 join events e on e.id = a.event_id
				 left join article_match_features f on f.article_id = a.article_id and f.model_version = e.model_version
				 where a.event_id = any(:events) and e.representative_article_id is null
				 order by a.event_id, %s)
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

	@Override
	public List<MergedEvent> mergedInto(long survivor) {
		return this.jdbc.sql("""
				select e.id, e.article_count, e.first_article_at, e.last_article_at, e.created_at, e.closed_at,
				       j.probability
				from events e
				left join event_merge_judgements j
				  on j.event_low = least(e.id, e.merged_into) and j.event_high = greatest(e.id, e.merged_into)
				where e.merged_into = :survivor
				order by e.closed_at, e.id
				""").param("survivor", survivor).query((rs, row) -> new MergedEvent(new EventId(rs.getLong("id")),
				rs.getInt("article_count"), instant(rs, "first_article_at"), instant(rs, "last_article_at"),
				instant(rs, "created_at"), instant(rs, "closed_at"), rs.getObject("probability", Double.class)))
			.list();
	}

	@Override
	public Map<Long, JudgementUse.Assignment> assignmentsByJudgement(Collection<Long> judgementIds) {
		if (judgementIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, JudgementUse.Assignment> assignments = new LinkedHashMap<>();
		this.jdbc.sql("""
				select judgement_id, article_id, event_id, decided_by from event_articles
				where judgement_id = any(:ids)
				order by judgement_id, article_id
				""").param("ids", judgementIds.toArray(Long[]::new)).query((ResultSet rs) -> {
					long judgement = rs.getLong("judgement_id");
					JudgementUse.Assignment assignment = new JudgementUse.Assignment(
							new ArticleId(rs.getLong("article_id")), new EventId(rs.getLong("event_id")),
							DecidedBy.valueOf(rs.getString("decided_by")));
					if (assignments.putIfAbsent(judgement, assignment) != null) {
						throw new IllegalStateException("판정 원본 하나를 배정 둘이 가리킨다: " + judgement);
					}
				});
		return Collections.unmodifiableMap(assignments);
	}

	@Override
	public Map<Long, List<MergePair>> mergePairsByJudgement(Collection<Long> judgementIds) {
		if (judgementIds.isEmpty()) {
			return Map.of();
		}
		// 두 사건을 각각 남는 사건으로 읽는다 — 가리킴은 평평해 한 번이면 된다
		Map<Long, List<MergePair>> pairs = new LinkedHashMap<>();
		this.jdbc.sql("""
				select j.judgement_id, j.event_low, j.event_high, j.probability,
				       coalesce(l.merged_into, l.id) as low_now, coalesce(h.merged_into, h.id) as high_now
				from event_merge_judgements j
				left join events l on l.id = j.event_low
				left join events h on h.id = j.event_high
				where j.judgement_id = any(:ids)
				order by j.judgement_id, j.event_low, j.event_high
				""").param("ids", judgementIds.toArray(Long[]::new)).query((ResultSet rs) -> {
					Long low = rs.getObject("low_now", Long.class);
					Long high = rs.getObject("high_now", Long.class);
					EventId survivor = (low != null && low.equals(high)) ? new EventId(low) : null;
					pairs.computeIfAbsent(rs.getLong("judgement_id"), judgement -> new ArrayList<>())
						.add(new MergePair(new EventId(rs.getLong("event_low")), new EventId(rs.getLong("event_high")),
								rs.getDouble("probability"), survivor));
				});
		Map<Long, List<MergePair>> result = new LinkedHashMap<>();
		pairs.forEach((judgement, found) -> result.put(judgement, List.copyOf(found)));
		return Collections.unmodifiableMap(result);
	}

	@Override
	public Map<Long, EventProfile> profiles(Collection<Long> eventIds, int keyTerms) {
		if (eventIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, EventProfile> profiles = new LinkedHashMap<>();
		this.jdbc.sql("select e.id, e.status, e.model_version, e.centroid::text as centroid, e.article_count, "
				+ "e.first_article_at, e.last_article_at, " + TOP_TERMS_WITH_COUNTS + """
				from events e
				where e.id = any(:ids) and e.status <> 'MERGED'
				order by e.id
				""")
			.param("ids", eventIds.toArray(Long[]::new))
			.param("terms", keyTerms)
			.query((ResultSet rs) -> {
				long id = rs.getLong("id");
				profiles.put(id, new EventProfile(new EventId(id), EventStatus.valueOf(rs.getString("status")),
						rs.getString("model_version"), VectorText.parse(rs.getString("centroid")),
						rs.getInt("article_count"), instant(rs, "first_article_at"), instant(rs, "last_article_at"),
						keyTerms(rs)));
			});
		return Collections.unmodifiableMap(profiles);
	}

	@Override
	public List<EventNeighbor> neighbors(long eventId, Instant from, Instant to, double minCosine, int keyTerms,
			int limit) {
		return this.jdbc.sql("select e.id, 1 - (e.centroid <=> b.centroid) as cosine, e.article_count, "
				+ "e.first_article_at, e.last_article_at, " + TOP_TERMS_WITH_COUNTS + """
				from events b
				join events e on e.model_version = b.model_version and e.id <> b.id and e.status <> 'MERGED'
				where b.id = :id and b.status <> 'MERGED'
				  and e.last_article_at >= :from and e.first_article_at <= :to
				  and (e.centroid <=> b.centroid) <> 'NaN'
				  and 1 - (e.centroid <=> b.centroid) >= :min
				order by cosine desc, e.id
				limit :limit
				""")
			.param("id", eventId)
			.param("from", timestamp(from))
			.param("to", timestamp(to))
			.param("min", minCosine)
			.param("terms", keyTerms)
			.param("limit", limit)
			.query((rs, row) -> new EventNeighbor(new EventId(rs.getLong("id")), rs.getDouble("cosine"),
					rs.getInt("article_count"), instant(rs, "first_article_at"), instant(rs, "last_article_at"),
					keyTerms(rs)))
			.list();
	}

	@Override
	public List<EventView> lastArticleSince(Instant since) {
		return this.jdbc.sql("select " + EVENT_COLUMNS + """
				from events e
				where e.status <> 'MERGED' and e.last_article_at >= :since
				order by e.first_article_at, e.id
				""").param("since", timestamp(since)).query(JdbcEventQueryRepository::event).list();
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

	private static List<EventKeyTerm> keyTerms(ResultSet rs) throws SQLException {
		String[] terms = strings(rs.getArray("key_terms"));
		Integer[] counts = (Integer[]) rs.getArray("key_counts").getArray();
		List<EventKeyTerm> keyTerms = new ArrayList<>(terms.length);
		for (int i = 0; i < terms.length; i++) {
			keyTerms.add(new EventKeyTerm(terms[i], counts[i]));
		}
		return List.copyOf(keyTerms);
	}

	private static String[] strings(Array array) throws SQLException {
		return (String[]) array.getArray();
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

}
