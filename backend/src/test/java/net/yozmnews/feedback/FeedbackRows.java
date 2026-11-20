package net.yozmnews.feedback;

import java.util.List;

import net.yozmnews.digest.DigestRows;
import net.yozmnews.event.EventArticles;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 도우미 — 피드백 시험의 사건 · 항목 행 넣기, 사건 현재 상태 · 이력 · 항목 기록 행 읽기, 정리. 사건 행은 SQL 로 넣는다(배정 없음).
 */
public final class FeedbackRows {

	private FeedbackRows() {
	}

	/**
	 * 열린 사건 하나를 넣는다 — 배정은 없다.
	 * @return 사건 ID
	 */
	public static long event(JdbcClient jdbc) {
		return jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at)
				values ('OPEN', 'model-a', cast('[1,0]' as halfvec), 1, now(), now(), now(), now())
				returning id
				""").query(Long.class).single();
	}

	/** 사건 하나를 남는 사건에 합쳐진 것으로 바꾼다 */
	public static void merge(JdbcClient jdbc, long merged, long survivor) {
		jdbc.sql("""
				update events set status = 'MERGED', merged_into = :survivor, closed_at = now() where id = :id
				""").param("survivor", survivor).param("id", merged).update();
	}

	/** 사건 행을 지운다 — {@code event} 의 삭제를 거치지 않는다(알림 없음) */
	public static void deleteEvent(JdbcClient jdbc, long event) {
		jdbc.sql("delete from events where id = :id").param("id", event).update();
	}

	/**
	 * 그 사용자의 그 날짜의 {@code READY} 다이제스트 하나에 항목 하나(사건들)를 넣는다.
	 * @return 다이제스트 ID
	 */
	public static long digestWithItem(JdbcClient jdbc, long user, String date, long... events) {
		long digest = DigestRows.digest(jdbc, user, date);
		DigestRows.item(jdbc, digest, events);
		return digest;
	}

	/** 그 사용자의 사건 현재 상태 행 — 사건 ID 순의 "사건:값:누른 다이제스트" */
	public static List<String> current(JdbcClient jdbc, long user) {
		return jdbc.sql("select event_id, value, digest_id from feedback where user_id = :user order by event_id")
			.param("user", user)
			.query((rs, row) -> rs.getLong("event_id") + ":" + rs.getString("value") + ":" + rs.getLong("digest_id"))
			.list();
	}

	/** 그 사용자 · 사건의 이력 행 — 행 ID 순의 "값:누른 다이제스트" */
	public static List<String> history(JdbcClient jdbc, long user, long event) {
		return jdbc.sql("""
				select value, digest_id from feedback_events where user_id = :user and event_id = :event order by id
				""")
			.param("user", user)
			.param("event", event)
			.query((rs, row) -> rs.getString("value") + ":" + rs.getLong("digest_id"))
			.list();
	}

	/** 그 사용자의 항목 기록 행 — (다이제스트, 항목 키) 순의 "다이제스트:항목 키:값" */
	public static List<String> items(JdbcClient jdbc, long user) {
		return jdbc.sql("""
				select digest_id, item_event_id, value from feedback_items where user_id = :user
				order by digest_id, item_event_id
				""")
			.param("user", user)
			.query((rs, row) -> rs.getLong("digest_id") + ":" + rs.getLong("item_event_id") + ":"
					+ rs.getString("value"))
			.list();
	}

	/** 세 테이블의 모든 행 수 */
	public static long rows(JdbcClient jdbc) {
		return jdbc.sql("""
				select (select count(*) from feedback) + (select count(*) from feedback_events)
				     + (select count(*) from feedback_items)
				""").query(Long.class).single();
	}

	/** 피드백 · 다이제스트 · 사건 · 기사 행을 모두 지운다 */
	public static void clear(JdbcClient jdbc) {
		jdbc.sql("delete from feedback").update();
		jdbc.sql("delete from feedback_events").update();
		jdbc.sql("delete from feedback_items").update();
		jdbc.sql("delete from digests").update();
		EventArticles.clear(jdbc);
	}

}
