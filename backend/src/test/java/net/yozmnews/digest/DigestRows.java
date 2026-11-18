package net.yozmnews.digest;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 도우미 — 다이제스트 · 섹션 · 인용 행. 모두 SQL 로 넣는다 — 생성 흐름 없이 인용을 가진 다이제스트를 만든다. 인용의 기사는 행만 있으면
 * 되므로 기사 테이블과 무관하다(모듈 사이라 FK 가 없다).
 */
public final class DigestRows {

	private DigestRows() {
	}

	/**
	 * 그 사용자의 그 날짜의 {@code READY} 다이제스트 하나를 넣는다.
	 * @return 다이제스트 ID
	 */
	public static long digest(JdbcClient jdbc, long user, String date) {
		return jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, created_at,
				    started_at, completed_at, version)
				values (:user, cast(:date as date), cast(:date as date) - interval '1 day', cast(:date as date), 'READY',
				    'SCHEDULED', now(), now(), now(), 0)
				returning id
				""").param("user", user).param("date", date).query(Long.class).single();
	}

	/**
	 * 그 사용자의 그 날짜의 {@code READY} 다이제스트 하나를 끝낸 시각과 함께 넣는다.
	 * @return 다이제스트 ID
	 */
	public static long digest(JdbcClient jdbc, long user, String date, String completedAt) {
		return jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, created_at,
				    started_at, completed_at, version)
				values (:user, cast(:date as date), cast(:date as date) - interval '1 day', cast(:date as date), 'READY',
				    'SCHEDULED', cast(:completed as timestamptz), cast(:completed as timestamptz),
				    cast(:completed as timestamptz), 0)
				returning id
				""").param("user", user).param("date", date).param("completed", completedAt).query(Long.class).single();
	}

	/** 다이제스트가 기사를 인용한다 — 추가 헤드라인 섹션 하나에 인용 하나 */
	public static void cite(JdbcClient jdbc, long digest, long article) {
		long section = jdbc.sql("""
				insert into digest_sections (digest_id, kind, position, title, body)
				values (:digest, 'MORE_HEADLINES', (select count(*) from digest_sections where digest_id = :digest), null, null)
				returning id
				""").param("digest", digest).query(Long.class).single();
		jdbc.sql("""
				insert into digest_items (digest_section_id, position, article_id, note, selection_reason)
				values (:section, 0, :article, null, 'SUBSCRIBED')
				""").param("section", section).param("article", article).update();
	}

}
