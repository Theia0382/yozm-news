package net.yozmnews.user.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedStatus;
import net.yozmnews.user.domain.notice.FeedStatusContent;
import net.yozmnews.user.domain.notice.FeedStatusEntry;
import net.yozmnews.user.domain.notice.KeyInvalidatedContent;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.NoticeContent;
import net.yozmnews.user.domain.notice.NoticeKind;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.PasswordChangeMethod;
import net.yozmnews.user.domain.notice.PasswordChangedContent;
import net.yozmnews.user.domain.notice.SeenNotice;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link NoticeRepository} 의 JdbcClient 구현 — docs/specs/user.md §4 · §6 · user-R21 · user-R23 ~ user-R26 · user-R36.
 * <p>
 * 잠금은 aggregate 저장이 아니라 SQL 이다. 반영 기준 행은 자리 행을 {@code INSERT … ON CONFLICT DO NOTHING} 으로 넣은 뒤
 * {@code SELECT … FOR UPDATE} 로 잠근다(위반 예외는 트랜잭션을 깨므로 쓰지 않는다). 안 읽은 알림의 잠금은 조건({@code read_at is null})을
 * 잠금을 얻은 뒤 다시 보므로, 그사이 읽음이 커밋되면 그 행을 고르지 않는다. 읽음은 받은 판이 그대로인 행만 바꾼다 — 그사이 내용이 고쳐진
 * 행은 판이 달라 안 읽음으로 남는다. 내용은 종류마다 저장 모양 레코드로 옮겨 {@code jsonb} 로 쓴다.
 */
@Repository
class JdbcNoticeRepository implements NoticeRepository {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String COLUMNS = "id, kind, content::text as content, revision, created_at, updated_at, read_at";

	private final JdbcClient jdbc;

	JdbcNoticeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Instant> lockFeedStatusReceipt(UserId user) {
		this.jdbc.sql("""
				insert into user_feed_status_receipts (user_id, received_through) values (:user, null)
				on conflict (user_id) do nothing
				""").param("user", user.value()).update();
		return this.jdbc.sql("select received_through from user_feed_status_receipts where user_id = :user for update")
			.param("user", user.value())
			.query((rs, row) -> Optional.ofNullable(instant(rs, "received_through")))
			.single();
	}

	@Override
	public void saveFeedStatusReceipt(UserId user, Instant receivedThrough) {
		this.jdbc.sql("update user_feed_status_receipts set received_through = :through where user_id = :user")
			.param("through", utc(receivedThrough))
			.param("user", user.value())
			.update();
	}

	@Override
	public Optional<Notice> lockUnread(UserId user, NoticeKind kind) {
		return this.jdbc.sql("""
				select %s from user_notices
				where user_id = :user and kind = :kind and read_at is null
				for update
				""".formatted(COLUMNS))
			.param("user", user.value())
			.param("kind", kind.name())
			.query(JdbcNoticeRepository::notice)
			.optional();
	}

	@Override
	public long create(UserId user, NoticeContent content, Instant now) {
		return this.jdbc.sql("""
				insert into user_notices (user_id, kind, content, revision, created_at, updated_at)
				values (:user, :kind, cast(:content as jsonb), 1, :now, :now)
				returning id
				""")
			.param("user", user.value())
			.param("kind", content.kind().name())
			.param("content", json(content))
			.param("now", utc(now))
			.query(Long.class)
			.single();
	}

	@Override
	public boolean createOnce(UserId user, KeyInvalidatedContent content, Instant now) {
		return this.jdbc.sql("""
				insert into user_notices (user_id, kind, content, revision, created_at, updated_at)
				values (:user, :kind, cast(:content as jsonb), 1, :now, :now)
				on conflict (user_id, (content ->> 'keyId'), (content ->> 'invalidatedAt'))
				  where kind = 'KEY_INVALIDATED' do nothing
				""")
			.param("user", user.value())
			.param("kind", content.kind().name())
			.param("content", json(content))
			.param("now", utc(now))
			.update() == 1;
	}

	@Override
	public void revise(long id, NoticeContent content, Instant now) {
		this.jdbc.sql("""
				update user_notices set content = cast(:content as jsonb), revision = revision + 1, updated_at = :now
				where id = :id
				""").param("content", json(content)).param("now", utc(now)).param("id", id).update();
	}

	@Override
	public List<Notice> page(UserId user, @Nullable Long before, int size) {
		String after = (before == null) ? "" : "and id < :before";
		JdbcClient.StatementSpec statement = this.jdbc.sql("""
				select %s from user_notices
				where user_id = :user %s
				order by id desc
				limit :size
				""".formatted(COLUMNS, after)).param("user", user.value()).param("size", size);
		if (before != null) {
			statement = statement.param("before", before);
		}
		return statement.query(JdbcNoticeRepository::notice).list();
	}

	@Override
	public int countUnread(UserId user) {
		return this.jdbc.sql("select count(*) from user_notices where user_id = :user and read_at is null")
			.param("user", user.value())
			.query(Integer.class)
			.single();
	}

	@Override
	public int markRead(UserId user, List<SeenNotice> seen, Instant now) {
		if (seen.isEmpty()) {
			return 0;
		}
		return this.jdbc.sql("""
				update user_notices n set read_at = :now
				from unnest(cast(:ids as bigint[]), cast(:revisions as integer[])) as s(id, revision)
				where n.id = s.id and n.revision = s.revision and n.user_id = :user and n.read_at is null
				""")
			.param("now", utc(now))
			.param("ids", seen.stream().map(SeenNotice::id).toArray(Long[]::new))
			.param("revisions", seen.stream().map(SeenNotice::revision).toArray(Integer[]::new))
			.param("user", user.value())
			.update();
	}

	@Override
	public int forgetReadBefore(Instant cutoff) {
		return this.jdbc.sql("delete from user_notices where read_at < :cutoff").param("cutoff", utc(cutoff)).update();
	}

	private static Notice notice(ResultSet rs, int row) throws SQLException {
		NoticeKind kind = NoticeKind.valueOf(rs.getString("kind"));
		NoticeContent content = switch (kind) {
			case FEED_STATUS -> JSON.readValue(rs.getString("content"), StoredFeedStatus.class).content();
			case PASSWORD_CHANGED -> JSON.readValue(rs.getString("content"), StoredPasswordChanged.class).content();
			case KEY_INVALIDATED -> JSON.readValue(rs.getString("content"), StoredKeyInvalidated.class).content();
		};
		return new Notice(rs.getLong("id"), kind, content, rs.getInt("revision"),
				rs.getObject("created_at", OffsetDateTime.class).toInstant(),
				rs.getObject("updated_at", OffsetDateTime.class).toInstant(), instant(rs, "read_at"));
	}

	private static String json(NoticeContent content) {
		return switch (content) {
			case FeedStatusContent feedStatus -> JSON.writeValueAsString(StoredFeedStatus.of(feedStatus));
			case PasswordChangedContent passwordChanged ->
				JSON.writeValueAsString(StoredPasswordChanged.of(passwordChanged));
			case KeyInvalidatedContent keyInvalidated -> JSON.writeValueAsString(StoredKeyInvalidated.of(keyInvalidated));
		};
	}

	private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
		@Nullable OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value != null) ? value.toInstant() : null;
	}

	private static OffsetDateTime utc(Instant value) {
		return value.atOffset(ZoneOffset.UTC);
	}

	/** 피드 상태 알림 내용의 저장 모양 — {@code {"feeds":[…]}} */
	private record StoredFeedStatus(List<StoredFeedEntry> feeds) {

		static StoredFeedStatus of(FeedStatusContent content) {
			return new StoredFeedStatus(content.feeds()
				.stream()
				.map(entry -> new StoredFeedEntry(entry.feedId().value(), entry.sourceName(), entry.section(),
						entry.firstFrom(), entry.lastTo(), entry.firstAt().toString(), entry.lastAt().toString(),
						entry.changes()))
				.toList());
		}

		FeedStatusContent content() {
			return new FeedStatusContent(this.feeds.stream()
				.map(entry -> new FeedStatusEntry(new FeedId(entry.feedId()), entry.sourceName(), entry.section(),
						entry.firstFrom(), entry.lastTo(), Instant.parse(entry.firstAt()), Instant.parse(entry.lastAt()),
						entry.changes()))
				.toList());
		}

	}

	/** 비밀번호 알림 내용의 저장 모양 — {@code {"method":…,"changedAt":…}}, 시각은 ISO 8601 문자열 */
	private record StoredPasswordChanged(PasswordChangeMethod method, String changedAt) {

		static StoredPasswordChanged of(PasswordChangedContent content) {
			return new StoredPasswordChanged(content.method(), content.changedAt().toString());
		}

		PasswordChangedContent content() {
			return new PasswordChangedContent(this.method, Instant.parse(this.changedAt));
		}

	}

	/**
	 * 키 무효 알림 내용의 저장 모양 — {@code {"provider":…,"keyId":…,"invalidatedAt":…}}. 공급자는 그 키({@code Provider.key()}), 시각은 ISO
	 * 8601 문자열이다. {@code keyId} · {@code invalidatedAt} 은 같은 전이의 알림을 하나로 묶는 고유 인덱스의 키다 — 이름을 바꾸면
	 * 마이그레이션의 인덱스도 바꾼다
	 */
	private record StoredKeyInvalidated(String provider, long keyId, String invalidatedAt) {

		static StoredKeyInvalidated of(KeyInvalidatedContent content) {
			return new StoredKeyInvalidated(content.provider().key(), content.keyId(),
					content.invalidatedAt().toString());
		}

		KeyInvalidatedContent content() {
			return new KeyInvalidatedContent(
					Provider.ofKey(this.provider)
						.orElseThrow(() -> new IllegalStateException("모르는 공급자 키가 저장돼 있다: " + this.provider)),
					this.keyId, Instant.parse(this.invalidatedAt));
		}

	}

	/** 피드 한 줄의 저장 모양 — 시각은 ISO 8601 문자열 */
	private record StoredFeedEntry(long feedId, @Nullable String sourceName, @Nullable String section,
			FeedStatus firstFrom, FeedStatus lastTo, String firstAt, String lastAt, int changes) {
	}

}
