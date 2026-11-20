package net.yozmnews.digest;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 도우미 — 다이제스트 · 섹션 · 섹션 사건 · 인용 행. 모두 SQL 로 넣는다 — 생성 흐름 없이 항목 · 인용을 가진 다이제스트를 만든다. 섹션
 * 사건의 사건과 인용의 기사는 행만 있으면 되므로 사건 · 기사 테이블과 무관하다(모듈 사이라 FK 가 없다). 다이제스트의 계기는 수동({@code MANUAL})이다 — 일정 다이제스트는 사용자 · 날짜마다
 * 하나라, 같은 날짜에 여럿을 넣는 시험이 걸리지 않게 한다.
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
				    dispatch_token, dispatched_at, started_at, completed_at, version)
				values (:user, cast(:date as date), cast(:date as date) - interval '1 day', cast(:date as date), 'READY',
				    'MANUAL', now(), gen_random_uuid(), now(), now(), now(), 0)
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
				    dispatch_token, dispatched_at, started_at, completed_at, version)
				values (:user, cast(:date as date), cast(:date as date) - interval '1 day', cast(:date as date), 'READY',
				    'MANUAL', cast(:completed as timestamptz), gen_random_uuid(), cast(:completed as timestamptz),
				    cast(:completed as timestamptz), cast(:completed as timestamptz), 0)
				returning id
				""").param("user", user).param("date", date).param("completed", completedAt).query(Long.class).single();
	}

	/**
	 * 다이제스트에 항목 하나를 넣는다 — 헤드라인 섹션 하나에 사건 행을 위치 0, 1 … 순으로. 인용 행은 없다.
	 * @return 섹션 ID
	 */
	public static long item(JdbcClient jdbc, long digest, long... events) {
		return section(jdbc, digest, "HEADLINE", events);
	}

	/**
	 * 다이제스트에 그 종류의 섹션 하나를 넣는다 — 사건 행을 위치 0, 1 … 순으로. 인용 행은 없다.
	 * @return 섹션 ID
	 */
	public static long section(JdbcClient jdbc, long digest, String kind, long... events) {
		long section = jdbc.sql("""
				insert into digest_sections (digest_id, kind, position, title, body)
				values (:digest, :kind, (select count(*) from digest_sections where digest_id = :digest), null, null)
				returning id
				""").param("digest", digest).param("kind", kind).query(Long.class).single();
		for (int position = 0; position < events.length; position++) {
			jdbc.sql("""
					insert into digest_section_events (digest_section_id, position, event_id, selection_reason)
					values (:section, :position, :event, 'SUBSCRIBED')
					""").param("section", section).param("position", position).param("event", events[position]).update();
		}
		return section;
	}

	/** 다이제스트가 기사를 인용한다 — 헤드라인 섹션 하나에 인용 하나 */
	public static void cite(JdbcClient jdbc, long digest, long article) {
		long section = jdbc.sql("""
				insert into digest_sections (digest_id, kind, position, title, body)
				values (:digest, 'HEADLINE', (select count(*) from digest_sections where digest_id = :digest), null, null)
				returning id
				""").param("digest", digest).query(Long.class).single();
		jdbc.sql("""
				insert into digest_items (digest_section_id, position, article_id, selection_reason)
				values (:section, 0, :article, 'SUBSCRIBED')
				""").param("section", section).param("article", article).update();
	}

}
