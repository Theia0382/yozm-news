package net.yozmnews.source.application;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;

/** 등록 · 운영 테스트가 행을 지우고 세는 SQL */
final class RegistryRows {

	private final JdbcClient jdbc;

	RegistryRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void deleteAll() {
		this.jdbc.sql("delete from source_fetch_slots").update();
		this.jdbc.sql("delete from feed_poll_states").update();
		this.jdbc.sql("delete from feeds").update();
		this.jdbc.sql("delete from sources").update();
	}

	int count(String table) {
		return this.jdbc.sql("select count(*) from " + table).query(Integer.class).single();
	}

	/** 소스의 피드 — "키:켜짐" 을 키 순서로 */
	List<String> feeds(long source) {
		return this.jdbc.sql("select feed_key, enabled from feeds where source_id = :source order by feed_key")
			.param("source", source)
			.query((rs, row) -> rs.getString("feed_key") + ":" + rs.getBoolean("enabled"))
			.list();
	}

	long feedId(long source, String key) {
		return this.jdbc.sql("select id from feeds where source_id = :source and feed_key = :key")
			.param("source", source)
			.param("key", key)
			.query(Long.class)
			.single();
	}

	/** 수집 상태 행이 없는 피드도 만든다 — 프로필에서 빠진 피드를 흉내 낸다 */
	long insertFeed(long source, String key) {
		return this.jdbc.sql("""
				insert into feeds (source_id, feed_key, enabled, version, created_at)
				values (:source, :key, false, 0, now()) returning id
				""").param("source", source).param("key", key).query(Long.class).single();
	}

	boolean hasPollState(long feed) {
		return this.jdbc.sql("select count(*) from feed_poll_states where feed_id = :feed")
			.param("feed", feed)
			.query(Integer.class)
			.single() == 1;
	}

}
