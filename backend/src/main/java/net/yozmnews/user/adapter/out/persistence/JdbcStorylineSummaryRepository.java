package net.yozmnews.user.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.summary.StorylineSummary;
import net.yozmnews.user.domain.summary.StorylineSummaryRepository;
import net.yozmnews.user.domain.summary.SummaryAttempt;
import net.yozmnews.user.domain.summary.SummaryText;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link StorylineSummaryRepository} 의 JdbcClient 구현. docs/specs/user.md §4 · §6
 */
@Repository
class JdbcStorylineSummaryRepository implements StorylineSummaryRepository {

	private final JdbcClient jdbc;

	JdbcStorylineSummaryRepository(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Override
	public List<StorylineSummary> load(UserId user) {
		Objects.requireNonNull(user, "user");
		return read(user, null);
	}

	@Override
	public Optional<StorylineSummary> find(UserId user, long userStorylineId) {
		Objects.requireNonNull(user, "user");
		return read(user, userStorylineId).stream().findFirst();
	}

	/** 사용자의 정리 — {@code only} 가 있으면 그 사용자 스토리라인 하나만 */
	private List<StorylineSummary> read(UserId user, Long only) {
		Map<Long, Map<Long, SummaryText>> tops = new HashMap<>();
		this.jdbc.sql("""
				select t.user_storyline_id, t.storyline_id, t.title, t.summary, t.recent
				from user_storyline_top_summaries t
				join user_storylines u on u.id = t.user_storyline_id
				where u.user_id = :user and (cast(:only as bigint) is null or t.user_storyline_id = :only)
				""").param("user", user.value()).param("only", only).query((rs, n) -> {
			tops.computeIfAbsent(rs.getLong("user_storyline_id"), ignored -> new HashMap<>())
				.put(rs.getLong("storyline_id"),
						new SummaryText(rs.getString("title"), rs.getString("summary"), rs.getString("recent")));
			return n;
		}).list();
		Map<Long, Map<Long, Long>> events = new HashMap<>();
		this.jdbc.sql("""
				select e.user_storyline_id, e.event_id, e.article_id
				from user_storyline_summary_events e
				join user_storylines u on u.id = e.user_storyline_id
				where u.user_id = :user and (cast(:only as bigint) is null or e.user_storyline_id = :only)
				""").param("user", user.value()).param("only", only).query((rs, n) -> {
			events.computeIfAbsent(rs.getLong("user_storyline_id"), ignored -> new HashMap<>())
				.put(rs.getLong("event_id"), rs.getLong("article_id"));
			return n;
		}).list();
		return this.jdbc.sql("""
				select s.user_storyline_id, s.title, s.summary, s.recent, s.provider, s.model, s.input_tokens,
				       s.output_tokens, s.created_at, s.updated_at, s.version
				from user_storyline_summaries s
				join user_storylines u on u.id = s.user_storyline_id
				where u.user_id = :user and (cast(:only as bigint) is null or s.user_storyline_id = :only)
				order by s.updated_at desc, s.user_storyline_id
				""").param("user", user.value()).param("only", only).query((rs, n) -> {
			long id = rs.getLong("user_storyline_id");
			return new StorylineSummary(id,
					new SummaryText(rs.getString("title"), rs.getString("summary"), rs.getString("recent")),
					tops.getOrDefault(id, Map.of()), events.getOrDefault(id, Map.of()), rs.getString("provider"),
					rs.getString("model"), rs.getInt("input_tokens"), rs.getInt("output_tokens"),
					rs.getObject("created_at", OffsetDateTime.class).toInstant(),
					rs.getObject("updated_at", OffsetDateTime.class).toInstant(), rs.getInt("version"));
		}).list();
	}

	@Override
	public boolean create(StorylineSummary summary) {
		Objects.requireNonNull(summary, "summary");
		int inserted = this.jdbc.sql("""
				insert into user_storyline_summaries
				  (user_storyline_id, title, summary, recent, provider, model, input_tokens, output_tokens,
				   created_at, updated_at, version)
				values (:id, :title, :summary, :recent, :provider, :model, :input, :output, :created, :updated, 0)
				on conflict (user_storyline_id) do nothing
				""")
			.param("id", summary.userStorylineId())
			.param("title", summary.whole().title())
			.param("summary", summary.whole().summary())
			.param("recent", summary.whole().recent())
			.param("provider", summary.provider())
			.param("model", summary.model())
			.param("input", summary.inputTokens())
			.param("output", summary.outputTokens())
			.param("created", utc(summary.createdAt()))
			.param("updated", utc(summary.updatedAt()))
			.update();
		if (inserted == 0) {
			return false;
		}
		writeParts(summary);
		return true;
	}

	@Override
	public boolean replace(StorylineSummary summary, int expectedVersion) {
		Objects.requireNonNull(summary, "summary");
		int updated = this.jdbc.sql("""
				update user_storyline_summaries
				set title = :title, summary = :summary, recent = :recent, provider = :provider, model = :model,
				    input_tokens = :input, output_tokens = :output, updated_at = :updated, version = version + 1
				where user_storyline_id = :id and version = :expected
				""")
			.param("id", summary.userStorylineId())
			.param("title", summary.whole().title())
			.param("summary", summary.whole().summary())
			.param("recent", summary.whole().recent())
			.param("provider", summary.provider())
			.param("model", summary.model())
			.param("input", summary.inputTokens())
			.param("output", summary.outputTokens())
			.param("updated", utc(summary.updatedAt()))
			.param("expected", expectedVersion)
			.update();
		if (updated == 0) {
			return false;
		}
		this.jdbc.sql("delete from user_storyline_top_summaries where user_storyline_id = :id")
			.param("id", summary.userStorylineId())
			.update();
		this.jdbc.sql("delete from user_storyline_summary_events where user_storyline_id = :id")
			.param("id", summary.userStorylineId())
			.update();
		writeParts(summary);
		return true;
	}

	private void writeParts(StorylineSummary summary) {
		summary.tops()
			.forEach((storyline, text) -> this.jdbc.sql("""
					insert into user_storyline_top_summaries (user_storyline_id, storyline_id, title, summary, recent)
					values (:id, :storyline, :title, :summary, :recent)
					""")
				.param("id", summary.userStorylineId())
				.param("storyline", storyline)
				.param("title", text.title())
				.param("summary", text.summary())
				.param("recent", text.recent())
				.update());
		summary.events()
			.forEach((event, article) -> this.jdbc.sql("""
					insert into user_storyline_summary_events (user_storyline_id, event_id, article_id)
					values (:id, :event, :article)
					""")
				.param("id", summary.userStorylineId())
				.param("event", event)
				.param("article", article)
				.update());
	}

	@Override
	public void delete(long userStorylineId) {
		this.jdbc.sql("delete from user_storyline_summaries where user_storyline_id = :id")
			.param("id", userStorylineId)
			.update();
	}

	@Override
	public List<Long> deleteHolding(Collection<Long> articleIds) {
		Objects.requireNonNull(articleIds, "articleIds");
		if (articleIds.isEmpty()) {
			return List.of();
		}
		List<Long> deleted = new ArrayList<>(this.jdbc.sql("""
				delete from user_storyline_summaries
				where user_storyline_id in (
				  select user_storyline_id from user_storyline_summary_events where article_id in (:articles))
				returning user_storyline_id
				""").param("articles", List.copyOf(articleIds)).query(Long.class).list());
		deleted.sort(null);
		return List.copyOf(deleted);
	}

	@Override
	public List<SummaryAttempt> attempts(UserId user) {
		Objects.requireNonNull(user, "user");
		return this.jdbc.sql("""
				select a.user_storyline_id, a.calls, a.total_input_tokens, a.total_output_tokens, a.failures,
				       a.failure_code, a.failed_at
				from user_storyline_summary_attempts a
				join user_storylines u on u.id = a.user_storyline_id
				where u.user_id = :user
				order by a.user_storyline_id
				""").param("user", user.value()).query((rs, n) -> {
			OffsetDateTime failedAt = rs.getObject("failed_at", OffsetDateTime.class);
			return new SummaryAttempt(rs.getLong("user_storyline_id"), rs.getInt("calls"),
					rs.getLong("total_input_tokens"), rs.getLong("total_output_tokens"), rs.getInt("failures"),
					rs.getString("failure_code"), (failedAt == null) ? null : failedAt.toInstant());
		}).list();
	}

	@Override
	public void recordStored(long userStorylineId, int inputTokens, int outputTokens) {
		this.jdbc.sql("""
				insert into user_storyline_summary_attempts
				  (user_storyline_id, calls, total_input_tokens, total_output_tokens, failures)
				values (:id, 1, :input, :output, 0)
				on conflict (user_storyline_id) do update
				set calls = user_storyline_summary_attempts.calls + 1,
				    total_input_tokens = user_storyline_summary_attempts.total_input_tokens + :input,
				    total_output_tokens = user_storyline_summary_attempts.total_output_tokens + :output,
				    failures = 0, failure_code = null, failed_at = null
				""").param("id", userStorylineId).param("input", inputTokens).param("output", outputTokens).update();
	}

	@Override
	public void recordDiscarded(long userStorylineId, int inputTokens, int outputTokens) {
		this.jdbc.sql("""
				insert into user_storyline_summary_attempts
				  (user_storyline_id, calls, total_input_tokens, total_output_tokens, failures)
				values (:id, 1, :input, :output, 0)
				on conflict (user_storyline_id) do update
				set calls = user_storyline_summary_attempts.calls + 1,
				    total_input_tokens = user_storyline_summary_attempts.total_input_tokens + :input,
				    total_output_tokens = user_storyline_summary_attempts.total_output_tokens + :output
				""").param("id", userStorylineId).param("input", inputTokens).param("output", outputTokens).update();
	}

	@Override
	public void recordFailed(long userStorylineId, int inputTokens, int outputTokens, String failureCode,
			Instant failedAt) {
		Objects.requireNonNull(failureCode, "failureCode");
		Objects.requireNonNull(failedAt, "failedAt");
		this.jdbc.sql("""
				insert into user_storyline_summary_attempts
				  (user_storyline_id, calls, total_input_tokens, total_output_tokens, failures, failure_code, failed_at)
				values (:id, 1, :input, :output, 1, :code, :at)
				on conflict (user_storyline_id) do update
				set calls = user_storyline_summary_attempts.calls + 1,
				    total_input_tokens = user_storyline_summary_attempts.total_input_tokens + :input,
				    total_output_tokens = user_storyline_summary_attempts.total_output_tokens + :output,
				    failures = user_storyline_summary_attempts.failures + 1, failure_code = :code, failed_at = :at
				""")
			.param("id", userStorylineId)
			.param("input", inputTokens)
			.param("output", outputTokens)
			.param("code", failureCode)
			.param("at", utc(failedAt))
			.update();
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
