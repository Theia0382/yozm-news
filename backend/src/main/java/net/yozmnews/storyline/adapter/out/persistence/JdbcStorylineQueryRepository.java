package net.yozmnews.storyline.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.yozmnews.storyline.domain.storyline.StoredStoryline;
import net.yozmnews.storyline.domain.storyline.StorylineQueryRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link StorylineQueryRepository} 의 JdbcClient 구현 — docs/specs/storyline.md §4.
 * <p>
 * 행의 투영({@link #COLUMNS} — 구성 사건 수는 소속 행 수)과 "합쳐지지 않은 · 주어진 사건이 든" 조건({@link #HAVING_EVENTS})이 한 벌이다.
 * 찾기는 합쳐진 행이면 {@code merged_into} 를 한 번 따른다 — 가리킴이 평평하다(storyline-R18).
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

	private final JdbcClient jdbc;

	JdbcStorylineQueryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<StoredStoryline> find(long storylineId) {
		return this.jdbc.sql("select " + COLUMNS + """
				from storylines s
				where s.id = coalesce((select m.merged_into from storylines m where m.id = :id), :id)
				""").param("id", storylineId).query(JdbcStorylineQueryRepository::storyline).optional();
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
	public List<StoredStoryline> page(Collection<Long> eventIds, @Nullable Instant afterLast, @Nullable Long afterId,
			int limit) {
		if (eventIds.isEmpty()) {
			return List.of();
		}
		String after = afterLast == null ? "" : "and (s.last_article_at, s.id) < (:afterLast, :afterId)";
		JdbcClient.StatementSpec statement = this.jdbc
			.sql("select " + COLUMNS + " from storylines s where " + HAVING_EVENTS + after
					+ " order by s.last_article_at desc, s.id desc limit :limit")
			.param("events", eventIds.toArray(Long[]::new))
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
