package net.yozmnews.article;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 집계 시험의 기사 · 엔트리 행을 판정을 거치지 않고 넣는다 — 판정 종류 · 추정 여부 · guid · 받은 시각을 시험이 정한다.
 * <p>
 * 다른 패키지의 시험이 쓰므로 public 이다. 해시는 값마다 달라야 하는 고유 열만 뜻이 있다.
 */
public final class EntryRows {

	private final JdbcClient jdbc;

	public EntryRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void deleteAll() {
		this.jdbc.sql("delete from article_feed_entries").update();
		this.jdbc.sql("delete from articles").update();
	}

	/** 기사 하나 — 정규 URL 은 넣을 때마다 다르다 */
	public long article() {
		String url = "https://entries.example/" + UUID.randomUUID();
		return this.jdbc.sql("""
				insert into articles (canonical_url, url_hash, title, title_key_hash, published_at,
				    published_at_estimated, first_seen_at)
				values (:url, :urlHash, '제목', :titleHash, '2026-10-20T00:00:00Z', false, '2026-10-20T00:00:00Z')
				returning id
				""")
			.param("url", url)
			.param("urlHash", sha256(url))
			.param("titleHash", sha256("제목"))
			.query(Long.class)
			.single();
	}

	/** 엔트리 하나 — 항목 URL 해시는 기사마다 다른 값이면 된다 */
	public void entry(long articleId, long sourceId, long feedId, String matchKind, boolean estimated,
			@Nullable String guid, Instant seenAt) {
		this.jdbc.sql("""
				insert into article_feed_entries (article_id, feed_id, source_id, item_url_hash, guid, guid_hash,
				    feed_categories, published_at_estimated, match_kind, seen_at)
				values (:article, :feed, :source, :itemUrlHash, :guid, :guidHash, '{}', :estimated, :kind, :seenAt)
				""")
			.param("article", articleId)
			.param("feed", feedId)
			.param("source", sourceId)
			.param("itemUrlHash", sha256("item-" + articleId))
			.param("guid", guid, Types.VARCHAR)
			.param("guidHash", (guid != null) ? sha256(guid) : null, Types.BINARY)
			.param("estimated", estimated)
			.param("kind", matchKind)
			.param("seenAt", seenAt.atOffset(ZoneOffset.UTC))
			.update();
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
