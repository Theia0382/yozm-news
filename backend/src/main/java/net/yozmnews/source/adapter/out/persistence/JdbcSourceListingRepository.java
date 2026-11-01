package net.yozmnews.source.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedStatus;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.registry.SourceListingRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link SourceListingRepository} 의 JdbcClient 구현 — docs/specs/source.md §4.
 * <p>
 * 수집 상태의 열 목록과 행 읽기는 {@link JdbcFeedPollStateRepository} 의 것을 쓴다.
 */
@Repository
class JdbcSourceListingRepository implements SourceListingRepository {

	private final JdbcClient jdbc;

	JdbcSourceListingRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<SourceRow> findAllSources() {
		return this.jdbc.sql("""
				select id, preset_key, profile, enabled, poll_interval_minutes, hidden_at
				from sources
				order by id
				""")
			.query((rs, row) -> new SourceRow(new SourceId(rs.getLong("id")), rs.getString("preset_key"),
					rs.getString("profile"), rs.getBoolean("enabled"), nullableInt(rs, "poll_interval_minutes"),
					nullableInstant(rs, "hidden_at")))
			.list();
	}

	@Override
	public List<FeedRow> findAllFeeds() {
		// 수집 상태 행이 없는 피드도 빠뜨리지 않는다 — 그 피드의 수집 상태 열은 모두 비고 poll 은 null 이다
		return this.jdbc.sql("select f.id, f.source_id, f.feed_key, f.enabled, f.poll_interval_minutes, f.hidden_at, "
				+ JdbcFeedPollStateRepository.COLUMNS + """
						, ps.status, ps.status_changed_at
						from feeds f
						left join feed_poll_states ps on ps.feed_id = f.id
						order by f.source_id, f.id
						""")
			.query((rs, row) -> new FeedRow(new FeedId(rs.getLong("id")), new SourceId(rs.getLong("source_id")),
					rs.getString("feed_key"), rs.getBoolean("enabled"), nullableInt(rs, "poll_interval_minutes"),
					nullableInstant(rs, "hidden_at"), poll(rs)))
			.list();
	}

	private static @Nullable PollRow poll(ResultSet rs) throws SQLException {
		if (rs.getObject("feed_id") == null) {
			return null;
		}
		OffsetDateTime changedAt = rs.getObject("status_changed_at", OffsetDateTime.class);
		return new PollRow(JdbcFeedPollStateRepository.state(rs), FeedStatus.valueOf(rs.getString("status")),
				(changedAt != null) ? changedAt.toInstant() : null);
	}

	private static @Nullable Instant nullableInstant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value != null) ? value.toInstant() : null;
	}

	private static @Nullable Integer nullableInt(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

}
