package net.yozmnews.storyline.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.storyline.domain.storyline.DirectMembership;
import net.yozmnews.storyline.domain.storyline.StoredStoryline;
import net.yozmnews.storyline.domain.storyline.StorylineQueryRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link StorylineQueryRepository} 의 JdbcClient 구현 — docs/specs/storyline.md §4.
 * <p>
 * 행의 투영({@link #COLUMNS} — 구성 사건 수는 소속 행 수)이 한 벌이다. 쪽은 주어진 스토리라인 ID 가운데 {@code (last_article_at, id)}
 * 커서다.
 * 찾기({@link #findAll} — {@link #find} 는 그 한 건)는 합쳐진 행이면 {@code merged_into} 를 한 번 따른다 — 가리킴이 평평하다(storyline-R18).
 */
@Repository
class JdbcStorylineQueryRepository implements StorylineQueryRepository {

	private static final String COLUMNS = """
			s.id, (select count(*) from storyline_members c where c.storyline_id = s.id) as event_count,
			s.article_count, s.first_article_at, s.last_article_at, s.representative_event_id, s.created_at
			""";

	private static final String HAVING_EVENTS = """
			s.merged_into is null
			and exists (select 1 from storyline_members m where m.storyline_id = s.id and m.event_id = any(:events))
			""";

	private static final String AMONG = """
			s.merged_into is null and s.id = any(:ids)
			""";

	private final JdbcClient jdbc;

	JdbcStorylineQueryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<StoredStoryline> find(long storylineId) {
		return Optional.ofNullable(findAll(List.of(storylineId)).get(storylineId));
	}

	@Override
	public Map<Long, StoredStoryline> findAll(Collection<Long> storylineIds) {
		if (storylineIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, StoredStoryline> found = new LinkedHashMap<>();
		this.jdbc.sql("select r.requested, " + COLUMNS + """
				from (select distinct requested from unnest(:ids) as u(requested)) r
				join storylines m on m.id = r.requested
				join storylines s on s.id = coalesce(m.merged_into, m.id)
				order by r.requested
				""")
			.param("ids", storylineIds.toArray(Long[]::new))
			.query((rs, row) -> Map.entry(rs.getLong("requested"), storyline(rs, row)))
			.list()
			.forEach(entry -> found.put(entry.getKey(), entry.getValue()));
		return found;
	}

	@Override
	public List<StoredStoryline> having(Collection<Long> eventIds) {
		if (eventIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc.sql("select " + COLUMNS + " from storylines s where " + HAVING_EVENTS + " order by s.id")
			.param("events", eventIds.toArray(Long[]::new))
			.query(JdbcStorylineQueryRepository::storyline)
			.list();
	}

	@Override
	public List<DirectMembership> directMemberships(Collection<Long> eventIds) {
		if (eventIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc.sql("""
				select m.event_id, m.storyline_id, m.score
				from storyline_members m
				join storylines s on s.id = m.storyline_id
				where s.merged_into is null and m.event_id = any(:events)
				""")
			.param("events", eventIds.toArray(Long[]::new))
			.query((rs, row) -> new DirectMembership(rs.getLong("event_id"), rs.getLong("storyline_id"),
					rs.getObject("score", Double.class)))
			.list();
	}

	@Override
	public List<StoredStoryline> byIds(Collection<Long> storylineIds) {
		if (storylineIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc.sql("select " + COLUMNS + " from storylines s where " + AMONG + " order by s.id")
			.param("ids", storylineIds.toArray(Long[]::new))
			.query(JdbcStorylineQueryRepository::storyline)
			.list();
	}

	@Override
	public List<StoredStoryline> pageOf(Collection<Long> storylineIds, @Nullable Instant afterLast,
			@Nullable Long afterId, int limit) {
		if (storylineIds.isEmpty()) {
			return List.of();
		}
		String after = afterLast == null ? "" : "and (s.last_article_at, s.id) < (:afterLast, :afterId)";
		JdbcClient.StatementSpec statement = this.jdbc
			.sql("select " + COLUMNS + " from storylines s where " + AMONG + after
					+ " order by s.last_article_at desc, s.id desc limit :limit")
			.param("ids", storylineIds.toArray(Long[]::new))
			.param("limit", limit);
		if (afterLast != null) {
			statement = statement.param("afterLast", afterLast.atOffset(ZoneOffset.UTC)).param("afterId", afterId);
		}
		return statement.query(JdbcStorylineQueryRepository::storyline).list();
	}

	private static StoredStoryline storyline(ResultSet rs, int row) throws SQLException {
		return new StoredStoryline(rs.getLong("id"), rs.getInt("event_count"), rs.getInt("article_count"),
				instant(rs, "first_article_at"), instant(rs, "last_article_at"), rs.getLong("representative_event_id"),
				instant(rs, "created_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

}
