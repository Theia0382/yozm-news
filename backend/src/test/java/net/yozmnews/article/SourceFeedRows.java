package net.yozmnews.article;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험의 소스 · 피드 행 — article 은 들어온 항목의 피드가 있는지 source 에 묻는다. 항목을 넣기 전에 실제 행을 만든다.
 */
final class SourceFeedRows {

	private final JdbcClient jdbc;

	SourceFeedRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** source 의 행을 자식부터 모두 지운다 */
	void deleteAll() {
		this.jdbc.sql("delete from source_fetch_slots").update();
		this.jdbc.sql("delete from feed_poll_states").update();
		this.jdbc.sql("delete from feeds").update();
		this.jdbc.sql("delete from source_attributions").update();
		this.jdbc.sql("delete from sources").update();
	}

	/** 꺼진 프리셋 소스 — 프리셋 키는 행을 가르기만 한다 */
	long source(String presetKey) {
		return this.jdbc
			.sql("insert into sources (preset_key, enabled, version, created_at) values (:key, false, 0, now()) "
					+ "returning id")
			.param("key", presetKey)
			.query(Long.class)
			.single();
	}

	/** 소스의 꺼진 피드 */
	long feed(long source, String feedKey) {
		return this.jdbc.sql("""
				insert into feeds (source_id, feed_key, enabled, version, created_at)
				values (:source, :key, false, 0, now()) returning id
				""").param("source", source).param("key", feedKey).query(Long.class).single();
	}

	/** 피드 행을 지운다 — 운영자가 그 피드를 지운 것처럼(수집 상태가 없는 피드) */
	void deleteFeed(long feed) {
		this.jdbc.sql("delete from feeds where id = :feed").param("feed", feed).update();
	}

	/** 피드를 숨긴다 */
	void hideFeed(long feed) {
		this.jdbc.sql("update feeds set hidden_at = now() where id = :feed").param("feed", feed).update();
	}

}
