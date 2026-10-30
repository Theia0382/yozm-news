package net.yozmnews.source.adapter.out.persistence;

import java.net.URI;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.fetch.FetchFailureReason;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.poll.FeedPollContext;
import net.yozmnews.source.domain.poll.FeedPollState;
import net.yozmnews.source.domain.poll.FeedPollStateRepository;
import net.yozmnews.source.domain.poll.ItemKey;
import net.yozmnews.source.domain.poll.LastError;
import net.yozmnews.source.domain.poll.Lease;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.PollInterval;
import net.yozmnews.source.domain.profile.PollIntervalSettings;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link FeedPollStateRepository} 의 JdbcClient 구현 — docs/specs/source.md §4.
 * <p>
 * 직전 항목 키는 {@code text[]} 다 — null 은 아직 성공한 수집이 없다는 뜻이라 빈 배열과 다르다.
 */
@Repository
class JdbcFeedPollStateRepository implements FeedPollStateRepository {

	static final String COLUMNS = """
			feed_id, next_poll_at, lease_token, lease_scheduled_at, etag, last_modified, last_polled_at, last_success_at,
			last_new_item_at, latest_published_at, consecutive_failures, consecutive_permanent_failures,
			last_error_reason, last_error_message, last_error_status, last_error_at, moved_to, last_overflow_at,
			overflow_count, previous_item_keys""";

	/** 피드 행과 피드 상태(status · status_changed_at — 피드 건강 검사가 쓴다)를 뺀 모든 열의 갱신 */
	private static final String SET_ALL = """
			next_poll_at = :nextPollAt, lease_token = :leaseToken, lease_scheduled_at = :leaseScheduledAt, etag = :etag,
			last_modified = :lastModified, last_polled_at = :lastPolledAt, last_success_at = :lastSuccessAt,
			last_new_item_at = :lastNewItemAt, latest_published_at = :latestPublishedAt,
			consecutive_failures = :consecutiveFailures, consecutive_permanent_failures = :consecutivePermanentFailures,
			last_error_reason = :lastErrorReason, last_error_message = :lastErrorMessage,
			last_error_status = :lastErrorStatus, last_error_at = :lastErrorAt, moved_to = :movedTo,
			last_overflow_at = :lastOverflowAt, overflow_count = :overflowCount, previous_item_keys = :previousItemKeys""";

	/** 수집 상태와 그 피드 · 소스의 운영 설정 */
	private static final String CONTEXT = "select " + COLUMNS + """
			, f.source_id, f.feed_key, s.poll_interval_minutes as source_interval_minutes,
			f.poll_interval_minutes as feed_interval_minutes
			from feed_poll_states ps
			join feeds f on f.id = ps.feed_id
			join sources s on s.id = f.source_id
			where s.enabled and f.enabled
			""";

	private final JdbcClient jdbc;

	JdbcFeedPollStateRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void insertIfAbsent(FeedPollState initial) {
		this.jdbc.sql("insert into feed_poll_states (" + COLUMNS + """
				) values (:feedId, :nextPollAt, :leaseToken, :leaseScheduledAt, :etag, :lastModified, :lastPolledAt,
				:lastSuccessAt, :lastNewItemAt, :latestPublishedAt, :consecutiveFailures, :consecutivePermanentFailures,
				:lastErrorReason, :lastErrorMessage, :lastErrorStatus, :lastErrorAt, :movedTo, :lastOverflowAt,
				:overflowCount, :previousItemKeys)
				on conflict (feed_id) do nothing
				""").params(parameters(initial)).update();
	}

	@Override
	public Optional<FeedPollState> find(FeedId feedId) {
		return this.jdbc.sql("select " + COLUMNS + " from feed_poll_states where feed_id = :feedId")
			.param("feedId", feedId.value())
			.query((rs, row) -> state(rs))
			.optional();
	}

	@Override
	public List<FeedPollContext> lockDue(Instant now, int limit) {
		return this.jdbc.sql(CONTEXT + """
				and ps.next_poll_at <= :now
				order by ps.next_poll_at
				limit :limit
				for update of ps skip locked
				""").param("now", timestamp(now)).param("limit", limit).query((rs, row) -> context(rs)).list();
	}

	@Override
	public Map<SourceId, Instant> lastScheduledBySource(Collection<SourceId> sources, Instant now) {
		if (sources.isEmpty()) {
			return Map.of();
		}
		Map<SourceId, Instant> last = new LinkedHashMap<>();
		this.jdbc.sql("""
				select f.source_id, max(ps.lease_scheduled_at) as last_scheduled
				from feed_poll_states ps
				join feeds f on f.id = ps.feed_id
				where f.source_id in (:sources) and ps.lease_token is not null and ps.next_poll_at > :now
				group by f.source_id
				""")
			.param("sources", sources.stream().map(SourceId::value).toList())
			.param("now", timestamp(now))
			.query(rs -> {
				last.put(new SourceId(rs.getLong("source_id")), instant(rs, "last_scheduled"));
			});
		return last;
	}

	@Override
	public void saveLocked(FeedPollState state) {
		this.jdbc.sql("update feed_poll_states set " + SET_ALL + " where feed_id = :feedId")
			.params(parameters(state))
			.update();
	}

	@Override
	public boolean replaceIfLeased(UUID token, FeedPollState next) {
		Map<String, @Nullable Object> values = parameters(next);
		values.put("token", token);
		return this.jdbc.sql("update feed_poll_states set " + SET_ALL
				+ " where feed_id = :feedId and lease_token = :token").params(values).update() == 1;
	}

	@Override
	public Optional<FeedPollContext> findLeased(FeedId feedId, UUID token) {
		return this.jdbc.sql(CONTEXT + "and ps.feed_id = :feedId and ps.lease_token = :token")
			.param("feedId", feedId.value())
			.param("token", token)
			.query((rs, row) -> context(rs))
			.optional();
	}

	private static FeedPollContext context(ResultSet rs) throws SQLException {
		return new FeedPollContext(state(rs), new SourceId(rs.getLong("source_id")), new FeedKey(rs.getString("feed_key")),
				new PollIntervalSettings(interval(rs, "source_interval_minutes"), interval(rs, "feed_interval_minutes")));
	}

	private static @Nullable PollInterval interval(ResultSet rs, String column) throws SQLException {
		Integer minutes = rs.getObject(column, Integer.class);
		return (minutes != null) ? PollInterval.ofMinutes(minutes).orElseThrow() : null;
	}

	/** 상태의 모든 열을 이름 붙은 매개변수로 옮긴다 — 이름은 열 이름의 camelCase */
	static Map<String, @Nullable Object> parameters(FeedPollState state) {
		Lease lease = state.lease();
		LastError error = state.lastError();
		List<ItemKey> keys = state.previousItemKeys();
		Map<String, @Nullable Object> values = new HashMap<>();
		values.put("feedId", state.feedId().value());
		values.put("nextPollAt", timestamp(state.nextPollAt()));
		values.put("leaseToken", (lease != null) ? lease.token() : null);
		values.put("leaseScheduledAt", (lease != null) ? timestamp(lease.scheduledAt()) : null);
		values.put("etag", state.fetchState().etag());
		values.put("lastModified", state.fetchState().lastModified());
		values.put("lastPolledAt", timestamp(state.lastPolledAt()));
		values.put("lastSuccessAt", timestamp(state.lastSuccessAt()));
		values.put("lastNewItemAt", timestamp(state.lastNewItemAt()));
		values.put("latestPublishedAt", timestamp(state.latestPublishedAt()));
		values.put("consecutiveFailures", state.consecutiveFailures());
		values.put("consecutivePermanentFailures", state.consecutivePermanentFailures());
		values.put("lastErrorReason", (error != null) ? error.reason().name() : null);
		values.put("lastErrorMessage", (error != null) ? error.message() : null);
		values.put("lastErrorStatus", (error != null) ? error.httpStatus() : null);
		values.put("lastErrorAt", (error != null) ? timestamp(error.at()) : null);
		values.put("movedTo", (state.movedTo() != null) ? state.movedTo().toString() : null);
		values.put("lastOverflowAt", timestamp(state.lastOverflowAt()));
		values.put("overflowCount", state.overflowCount());
		values.put("previousItemKeys", (keys != null) ? keys.stream().map(ItemKey::value).toArray(String[]::new) : null);
		return values;
	}

	static FeedPollState state(ResultSet rs) throws SQLException {
		UUID token = rs.getObject("lease_token", UUID.class);
		Lease lease = (token != null) ? new Lease(token, instant(rs, "lease_scheduled_at")) : null;
		String reason = rs.getString("last_error_reason");
		LastError error = (reason != null) ? new LastError(FetchFailureReason.valueOf(reason),
				rs.getString("last_error_message"), rs.getObject("last_error_status", Integer.class),
				instant(rs, "last_error_at")) : null;
		String movedTo = rs.getString("moved_to");
		return new FeedPollState(new FeedId(rs.getLong("feed_id")), instant(rs, "next_poll_at"), lease,
				new FetchState(rs.getString("etag"), rs.getString("last_modified")), nullableInstant(rs, "last_polled_at"),
				nullableInstant(rs, "last_success_at"), nullableInstant(rs, "last_new_item_at"),
				nullableInstant(rs, "latest_published_at"), rs.getInt("consecutive_failures"),
				rs.getInt("consecutive_permanent_failures"), error, (movedTo != null) ? URI.create(movedTo) : null,
				nullableInstant(rs, "last_overflow_at"), rs.getInt("overflow_count"), itemKeys(rs));
	}

	private static @Nullable List<ItemKey> itemKeys(ResultSet rs) throws SQLException {
		Array array = rs.getArray("previous_item_keys");
		if (array == null) {
			return null;
		}
		return Arrays.stream((String[]) array.getArray()).map(ItemKey::new).toList();
	}

	private static @Nullable OffsetDateTime timestamp(@Nullable Instant instant) {
		return (instant != null) ? instant.atOffset(ZoneOffset.UTC) : null;
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Instant value = nullableInstant(rs, column);
		if (value == null) {
			throw new IllegalStateException("열이 비어 있다: " + column);
		}
		return value;
	}

	private static @Nullable Instant nullableInstant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value != null) ? value.toInstant() : null;
	}

}
