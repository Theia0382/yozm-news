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
		return insert(jdbc, title, summary, 1, 11);
	}

	/**
	 * 기사 하나와 엔트리 하나(고른 소스 · 피드)를 넣는다.
	 * @return 기사 ID
	 */
	public static long insert(JdbcClient jdbc, String title, @Nullable String summary, long source, long feed) {
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
				values (:article, :feed, :source, sha256(convert_to(:url, 'UTF8')), '{}', false, 'NEW',
				    '2026-11-05T00:00:00Z')
				""").param("article", id).param("feed", feed).param("source", source).param("url", url).update();
		return id;
	}

	/**
	 * 열린 사건 하나와 그 사건에 주어진 기사들의 배정을 넣는다 — 배정의 발행 시각 · 기준 · 사건 표현은 시험용 값이다. 기사가 없으면 배정 없는
	 * 사건이다(합쳐질 사건 등).
	 * @return 사건 ID
	 */
	public static long event(JdbcClient jdbc, long... articles) {
		long event = jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at)
				values ('OPEN', 'model-a', cast('[1,0]' as halfvec), :count, now(), now(), now(), now())
				returning id
				""").param("count", Math.max(1, articles.length)).query(Long.class).single();
		if (articles.length == 0) {
			return event;
		}
		// 기준 행은 값마다 하나다 — 이미 있으면 그 행을 쓴다
		long criteria = jdbc.sql("""
				with inserted as (
				    insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				    values (259200, 0.8, 0.55, 0.4, 0.5, now())
				    on conflict do nothing
				    returning id)
				select id from inserted
				union all
				select id from event_assignment_criteria
				where window_seconds = 259200 and upper = 0.8 and lower = 0.55 and store = 0.4
				limit 1
				""").query(Long.class).single();
		for (long article : articles) {
			jdbc.sql("""
					insert into event_articles (article_id, event_id, published_at, score, decided_by, opened, criteria_id,
					                            assigned_at)
					values (:article, :event, '2026-11-05T00:00:00Z', 0.9, 'FORMULA', false, :criteria, now())
					""").param("article", article).param("event", event).param("criteria", criteria).update();
		}
		return event;
	}

	/**
	 * 기사 · 엔트리와 사건 묶기 · 배정 · 평가 대기, 기사 표현, 사건 · 배정 · 후보 · 기준 이력 행을 모두 지운다 — 기사를 저장하면 평가 대기에도
	 * 들어간다
	 */
	public static void clear(JdbcClient jdbc) {
		jdbc.sql("delete from event_article_candidates").update();
		jdbc.sql("delete from event_merge_judgements").update();
		jdbc.sql("delete from event_articles").update();
		jdbc.sql("delete from events").update();
		jdbc.sql("delete from event_assignment_criteria").update();
		jdbc.sql("delete from event_assignment_queue").update();
		jdbc.sql("delete from article_match_features").update();
		jdbc.sql("delete from event_intake_queue").update();
		jdbc.sql("delete from evaluation_queue").update();
		jdbc.sql("delete from article_feed_entries").update();
		jdbc.sql("delete from articles").update();
	}

}
