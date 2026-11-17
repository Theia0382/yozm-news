package net.yozmnews.feedback.adapter.out.persistence;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.feedback.domain.record.FeedbackRepository;
import net.yozmnews.platform.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link FeedbackRepository} 의 JdbcClient 구현 — docs/specs/feedback.md §4 · feedback-R1 · feedback-R3 ~ feedback-R5.
 */
@Repository
class JdbcFeedbackRepository implements FeedbackRepository {

	private final JdbcClient jdbc;

	JdbcFeedbackRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean change(UserId user, ArticleId article, FeedbackValue value, long digestId, Instant now) {
		return switch (value) {
			case NONE -> this.jdbc.sql("""
					delete from feedback where user_id = :user and article_id = :article
					returning 1
					""").param("user", user.value()).param("article", article.value()).query(Integer.class).optional()
				.isPresent();
			case LIKE, DISLIKE -> this.jdbc.sql("""
					insert into feedback (user_id, article_id, value, digest_id, updated_at)
					values (:user, :article, :value, :digest, :at)
					on conflict (user_id, article_id) do update
					    set value = excluded.value, digest_id = excluded.digest_id, updated_at = excluded.updated_at
					    where feedback.value <> excluded.value
					returning 1
					""")
				.param("user", user.value())
				.param("article", article.value())
				.param("value", value.name())
				.param("digest", digestId)
				.param("at", utc(now))
				.query(Integer.class)
				.optional()
				.isPresent();
		};
	}

	@Override
	public void append(UserId user, ArticleId article, FeedbackValue value, long digestId, Instant now) {
		this.jdbc.sql("""
				insert into feedback_events (user_id, article_id, value, digest_id, occurred_at)
				values (:user, :article, :value, :digest, :at)
				""")
			.param("user", user.value())
			.param("article", article.value())
			.param("value", value.name())
			.param("digest", digestId)
			.param("at", utc(now))
			.update();
	}

	@Override
	public Map<ArticleId, FeedbackValue> currentAmong(UserId user, Collection<ArticleId> articles) {
		if (articles.isEmpty()) {
			return Map.of();
		}
		Map<ArticleId, FeedbackValue> current = new LinkedHashMap<>();
		this.jdbc.sql("""
				select article_id, value from feedback
				where user_id = :user and article_id = any(:articles)
				order by article_id
				""")
			.param("user", user.value())
			.param("articles", articles.stream().map(ArticleId::value).distinct().toArray(Long[]::new))
			.query((ResultSet rs) -> {
				current.put(new ArticleId(rs.getLong("article_id")), FeedbackValue.valueOf(rs.getString("value")));
			});
		return Collections.unmodifiableMap(current);
	}

	@Override
	public List<FeedbackChange> changes(UserId user, Instant from, Instant to) {
		return this.jdbc.sql("""
				select article_id, value, digest_id, occurred_at from feedback_events
				where user_id = :user and occurred_at >= :from and occurred_at < :to
				order by id
				""")
			.param("user", user.value())
			.param("from", utc(from))
			.param("to", utc(to))
			.query((rs, row) -> new FeedbackChange(new ArticleId(rs.getLong("article_id")),
					FeedbackValue.valueOf(rs.getString("value")), rs.getLong("digest_id"),
					rs.getObject("occurred_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	@Override
	public void removeAll(Collection<ArticleId> articles) {
		if (articles.isEmpty()) {
			return;
		}
		Long[] ids = articles.stream().map(ArticleId::value).distinct().toArray(Long[]::new);
		this.jdbc.sql("delete from feedback where article_id = any(:ids)").param("ids", ids).update();
		this.jdbc.sql("delete from feedback_events where article_id = any(:ids)").param("ids", ids).update();
	}

	private static OffsetDateTime utc(Instant value) {
		return value.atOffset(ZoneOffset.UTC);
	}

}
