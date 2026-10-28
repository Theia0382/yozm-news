package net.yozmnews.article;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;

/** 기사 · 엔트리 행을 읽고 비운다 — 커밋 경계를 지나는 시험은 트랜잭션으로 감싸지 않아 테스트마다 비운다. */
final class ArticleRows {

	private final JdbcClient jdbc;

	ArticleRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void deleteAll() {
		this.jdbc.sql("delete from article_feed_entries").update();
		this.jdbc.sql("delete from articles").update();
	}

	List<Long> articleIds() {
		return this.jdbc.sql("select id from articles order by id").query(Long.class).list();
	}

	/** 엔트리 (기사, 피드, 판정 종류) — 피드 · 기사 순 */
	List<Entry> entries() {
		return this.jdbc.sql("select article_id, feed_id, match_kind from article_feed_entries order by feed_id, article_id")
			.query((rs, rowNum) -> new Entry(rs.getLong("article_id"), rs.getLong("feed_id"), rs.getString("match_kind")))
			.list();
	}

	/** 두 테이블의 모든 열 — 해시 · 배열은 글자로 바꿔 값으로 비교할 수 있게 한다 */
	Snapshot snapshot() {
		List<Map<String, Object>> articles = this.jdbc.sql("""
				select id, source_id, canonical_url, encode(url_hash, 'hex') as url_hash, title,
				    encode(title_key_hash, 'hex') as title_key_hash, summary_text, author, published_at::text,
				    published_at_estimated, image_url, first_seen_at::text
				from articles order by id
				""").query().listOfRows();
		List<Map<String, Object>> entries = this.jdbc.sql("""
				select article_id, feed_id, encode(item_url_hash, 'hex') as item_url_hash, guid,
				    encode(guid_hash, 'hex') as guid_hash, feed_categories::text, published_at_estimated, match_kind,
				    seen_at::text
				from article_feed_entries order by article_id, feed_id
				""").query().listOfRows();
		return new Snapshot(articles, entries);
	}

	record Entry(long articleId, long feedId, String matchKind) {
	}

	record Snapshot(List<Map<String, Object>> articles, List<Map<String, Object>> entries) {
	}

}
