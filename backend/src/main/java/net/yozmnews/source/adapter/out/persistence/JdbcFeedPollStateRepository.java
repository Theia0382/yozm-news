package net.yozmnews.source.adapter.out.persistence;

import java.net.URI;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.domain.fetch.FetchFailureReason;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.poll.FeedPollState;
import net.yozmnews.source.domain.poll.FeedPollStateRepository;
import net.yozmnews.source.domain.poll.ItemKey;
import net.yozmnews.source.domain.poll.LastError;
import net.yozmnews.source.domain.poll.Lease;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code feed_poll_states} 를 JdbcClient 로 읽고 쓴다. 쓰기의 조건이 점유 토큰이라 aggregate 저장(버전 조건) 대신 SQL 로 쓴다.
 * 직전 항목 키는 {@code text[]} 다 — null 은 아직 성공한 수집이 없다는 뜻이라 빈 배열과 다르다.
 */
@Repository
class JdbcFeedPollStateRepository implements FeedPollStateRepository {

	static final String COLUMNS = """
			feed_id, next_poll_at, lease_token, lease_scheduled_at, etag, last_modified, last_polled_at, last_success_at,
			last_new_item_at, consecutive_failures, last_error_reason, last_error_message, last_error_status,
			last_error_at, moved_to, last_overflow_at, overflow_count, previous_item_keys""";

	private final JdbcClient jdbc;

	JdbcFeedPollStateRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void insertIfAbsent(FeedPollState initial) {
		this.jdbc.sql("insert into feed_poll_states (" + COLUMNS + """
				) values (:feedId, :nextPollAt, :leaseToken, :leaseScheduledAt, :etag, :lastModified, :lastPolledAt,
				:lastSuccessAt, :lastNewItemAt, :consecutiveFailures, :lastErrorReason, :lastErrorMessage,
				:lastErrorStatus, :lastErrorAt, :movedTo, :lastOverflowAt, :overflowCount, :previousItemKeys)
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

	/** 상태의 모든 열을 이름 붙은 매개변수로 — 열 이름의 camelCase */
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
		values.put("consecutiveFailures", state.consecutiveFailures());
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
				rs.getInt("consecutive_failures"), error, (movedTo != null) ? URI.create(movedTo) : null,
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
