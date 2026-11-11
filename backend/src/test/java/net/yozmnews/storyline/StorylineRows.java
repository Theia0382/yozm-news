package net.yozmnews.storyline;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import net.yozmnews.event.EventArticles;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 도우미 — 스토리라인 흐름 시험의 사건 행과 정리. 사건은 SQL 로 넣는다 — {@code EventsChanged} 가 나가지 않으므로 대기에는 시험이 직접
 * 넣는다.
 */
public final class StorylineRows {

	private StorylineRows() {
	}

	/** 스토리라인 테이블과 사건 묶기 테이블을 비운다 — 기준 이력은 남긴다 */
	public static void clear(JdbcClient jdbc) {
		jdbc.sql("delete from storyline_members").update();
		jdbc.sql("delete from storyline_key_terms").update();
		jdbc.sql("delete from storylines").update();
		jdbc.sql("delete from storyline_linking_queue").update();
		EventArticles.clear(jdbc);
	}

	/**
	 * 사건 하나와 그 핵심 말을 넣는다.
	 * @param centroid pgvector 텍스트 — 반 정밀도에서 정확한 값을 쓴다
	 * @return 사건 ID
	 */
	public static long event(JdbcClient jdbc, String status, String model, String centroid, int articles, String first,
			String last, Map<String, Integer> terms) {
		long id = jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at, closed_at)
				values (:status, :model, cast(:centroid as halfvec), :articles, :first, :last, :first, :last,
				        case when :status = 'OPEN' then null else cast(:last as timestamptz) end)
				returning id
				""")
			.param("status", status)
			.param("model", model)
			.param("centroid", centroid)
			.param("articles", articles)
			.param("first", Instant.parse(first).atOffset(ZoneOffset.UTC))
			.param("last", Instant.parse(last).atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
		terms.forEach((term, count) -> jdbc
			.sql("insert into event_key_terms (event_id, term, article_count) values (:event, :term, :count)")
			.param("event", id)
			.param("term", term)
			.param("count", count)
			.update());
		return id;
	}

	/** 사건을 다른 사건에 합쳐진 것으로 바꾼다 */
	public static void merge(JdbcClient jdbc, long merged, long survivor) {
		jdbc.sql("delete from event_key_terms where event_id = :id").param("id", merged).update();
		jdbc.sql("update events set status = 'MERGED', merged_into = :into, closed_at = now() where id = :id")
			.param("into", survivor)
			.param("id", merged)
			.update();
	}

	/** 사건 행을 지운다 — 핵심 말은 함께 지워진다 */
	public static void delete(JdbcClient jdbc, long event) {
		jdbc.sql("delete from events where id = :id or merged_into = :id").param("id", event).update();
	}

}
