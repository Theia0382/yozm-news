package net.yozmnews.source.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.registry.SourceListingRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link SourceListingRepository} 의 JdbcClient 구현 — docs/specs/source.md §4
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
				select id, preset_key, profile, enabled, poll_interval_minutes
				from sources
				order by id
				""")
			.query((rs, row) -> new SourceRow(new SourceId(rs.getLong("id")), rs.getString("preset_key"),
					rs.getString("profile"), rs.getBoolean("enabled"), nullableInt(rs, "poll_interval_minutes")))
			.list();
	}

	@Override
	public List<FeedRow> findAllFeeds() {
		return this.jdbc.sql("""
				select id, source_id, feed_key, enabled, poll_interval_minutes
				from feeds
				order by source_id, id
				""")
			.query((rs, row) -> new FeedRow(new FeedId(rs.getLong("id")), new SourceId(rs.getLong("source_id")),
					rs.getString("feed_key"), rs.getBoolean("enabled"), nullableInt(rs, "poll_interval_minutes")))
			.list();
	}

	private static @Nullable Integer nullableInt(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

}
