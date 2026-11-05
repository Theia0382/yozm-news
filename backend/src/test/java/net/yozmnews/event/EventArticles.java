package net.yozmnews.event;

import java.sql.Types;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 도우미 — 사건 묶기 시험의 기사 행과 정리. 기사 행은 SQL 로 넣는다(평가 흐름 시험과 같은 방식). 시험 문장은 지어낸 것이다.
 */
public final class EventArticles {

	private EventArticles() {
	}

	/**
	 * 기사 하나와 엔트리 하나(소스 1 · 피드 11)를 넣는다.
	 * @return 기사 ID
	 */
	public static long insert(JdbcClient jdbc, String title, @Nullable String summary) {
		String url = "https://event.example/" + UUID.randomUUID();
		long id = jdbc.sql("""
				insert into articles (canonical_url, url_hash, title, title_key_hash, summary_text, published_at,
				    published_at_estimated, first_seen_at)
				values (:url, sha256(convert_to(:url, 'UTF8')), :title, sha256(convert_to(:title, 'UTF8')), :summary,
				    '2026-11-05T00:00:00Z', false, '2026-11-05T00:00:00Z')
				returning id
				""").param("url", url).param("title", title).param("summary", summary, Types.VARCHAR)
			.query(Long.class)
			.single();
		jdbc.sql("""
				insert into article_feed_entries (article_id, feed_id, source_id, item_url_hash, feed_categories,
				    published_at_estimated, match_kind, seen_at)
				values (:article, 11, 1, sha256(convert_to(:url, 'UTF8')), '{}', false, 'NEW', '2026-11-05T00:00:00Z')
				""").param("article", id).param("url", url).update();
		return id;
	}

	/** 기사 · 엔트리와 사건 묶기 · 평가 대기, 기사 표현 행을 모두 지운다 — 기사를 저장하면 평가 대기에도 들어간다 */
	public static void clear(JdbcClient jdbc) {
		jdbc.sql("delete from article_match_features").update();
		jdbc.sql("delete from event_intake_queue").update();
		jdbc.sql("delete from evaluation_queue").update();
		jdbc.sql("delete from article_feed_entries").update();
		jdbc.sql("delete from articles").update();
	}

}
