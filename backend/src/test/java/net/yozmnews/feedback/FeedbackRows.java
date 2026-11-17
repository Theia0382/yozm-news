package net.yozmnews.feedback;

import java.util.List;

import net.yozmnews.event.EventArticles;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 도우미 — 피드백 시험의 기사 행 넣기, 현재 상태 · 이력 행 읽기, 정리. 시험 문장은 지어낸 것이다.
 */
public final class FeedbackRows {

	private FeedbackRows() {
	}

	/**
	 * 기사 하나(엔트리 하나)를 넣는다.
	 * @return 기사 ID
	 */
	public static long article(JdbcClient jdbc, String title) {
		return EventArticles.insert(jdbc, title, null);
	}

	/** 그 사용자의 현재 상태 행 — 기사 ID 순의 "기사:값:누른 다이제스트" */
	public static List<String> current(JdbcClient jdbc, long user) {
		return jdbc.sql("select article_id, value, digest_id from feedback where user_id = :user order by article_id")
			.param("user", user)
			.query((rs, row) -> rs.getLong("article_id") + ":" + rs.getString("value") + ":" + rs.getLong("digest_id"))
			.list();
	}

	/** 그 사용자 · 기사의 이력 행 — 행 ID 순의 "값:누른 다이제스트" */
	public static List<String> history(JdbcClient jdbc, long user, long article) {
		return jdbc.sql("""
				select value, digest_id from feedback_events where user_id = :user and article_id = :article order by id
				""")
			.param("user", user)
			.param("article", article)
			.query((rs, row) -> rs.getString("value") + ":" + rs.getLong("digest_id"))
			.list();
	}

	/** 두 테이블의 모든 행 수 */
	public static long rows(JdbcClient jdbc) {
		return jdbc.sql("select (select count(*) from feedback) + (select count(*) from feedback_events)")
			.query(Long.class)
			.single();
	}

	/** 피드백 · 다이제스트 · 기사 행을 모두 지운다 */
	public static void clear(JdbcClient jdbc) {
		jdbc.sql("delete from feedback").update();
		jdbc.sql("delete from feedback_events").update();
		jdbc.sql("delete from digests").update();
		EventArticles.clear(jdbc);
	}

}
