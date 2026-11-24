package net.yozmnews.user;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험의 계정 정리 도우미 — 계정과 함께 그 계정의 관심 · 알림 행을 지운다. 관심 · 알림 표는 계정을 ID 로만 가리켜(모듈 사이라 FK 가 없다)
 * 계정을 지워도 함께 지워지지 않으므로, 시험이 남긴 행이 다음 시험에 새지 않게 여기서 함께 지운다. 사용자 스토리라인에 딸린 표(추적 대상 · 추적
 * 씨앗 · 구독 · 정리 · 시도 기록)는 사용자 스토리라인을 지우면 함께 지워진다. 계정을 ID 로 가리키는 사용자 표를 더하는 변경은 이
 * 목록({@code OWNED})도 고친다 — 빠뜨리면 그 표의 행이 다음 시험에 남는다.
 */
public final class UserTables {

	/** 계정을 ID 로 가리키는 관심 · 알림 표 */
	private static final List<String> OWNED = List.of("user_notices", "user_feed_status_receipts", "user_settings",
			"user_tag_preferences", "user_tag_preference_events", "user_excluded_sources",
			"user_storyline_subscription_events", "user_topic_suggestion_declines", "user_storylines");

	private UserTables() {
	}

	/** 모든 계정과 그 관심 · 알림 행을 지운다 */
	public static void deleteAll(JdbcClient jdbc) {
		for (String table : OWNED) {
			jdbc.sql("delete from " + table).update();
		}
		jdbc.sql("delete from users").update();
	}

	/** 계정 하나와 그 관심 · 알림 행을 지운다 */
	public static void delete(JdbcClient jdbc, long user) {
		deleteIds(jdbc, new Long[] { user });
	}

	/** 이메일이 이 {@code like} 꼴에 맞는 계정과 그 관심 · 알림 행을 지운다 */
	public static void deleteByEmail(JdbcClient jdbc, String pattern) {
		deleteIds(jdbc, jdbc.sql("select id from users where email like :pattern")
			.param("pattern", pattern)
			.query(Long.class)
			.list()
			.toArray(Long[]::new));
	}

	private static void deleteIds(JdbcClient jdbc, Long[] users) {
		for (String table : OWNED) {
			jdbc.sql("delete from " + table + " where user_id = any(:users)").param("users", users).update();
		}
		jdbc.sql("delete from users where id = any(:users)").param("users", users).update();
	}

}
