package net.yozmnews.user.adapter.out.persistence;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.domain.suggestion.RecordedDecline;
import net.yozmnews.user.domain.suggestion.TopicSuggestionDeclineRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link TopicSuggestionDeclineRepository} 의 PostgreSQL 구현 — {@code user_topic_suggestion_declines}. docs/specs/user.md §4 ·
 * §6
 */
@Repository
class JdbcTopicSuggestionDeclines implements TopicSuggestionDeclineRepository {

	private final JdbcClient jdbc;

	JdbcTopicSuggestionDeclines(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Override
	public void upsert(UserId user, RecordedDecline decline) {
		this.jdbc.sql("""
				insert into user_topic_suggestion_declines (user_id, tag_set_id, topic_key, interest_level, declined_at)
				values (:user, :tagSet, :key, :level, :at)
				on conflict (user_id, tag_set_id, topic_key)
				do update set interest_level = excluded.interest_level, declined_at = excluded.declined_at
				""")
			.param("user", user.value())
			.param("tagSet", decline.version().value())
			.param("key", decline.key())
			.param("level", decline.level())
			.param("at", Timestamp.from(decline.declinedAt()))
			.update();
	}

	@Override
	public List<RecordedDecline> load(UserId user) {
		return this.jdbc.sql("""
				select tag_set_id, topic_key, interest_level, declined_at from user_topic_suggestion_declines
				where user_id = :user
				""")
			.param("user", user.value())
			.query((rs, n) -> new RecordedDecline(new TagSetId(rs.getLong(1)), rs.getString(2), rs.getDouble(3),
					rs.getTimestamp(4).toInstant()))
			.list();
	}

}
