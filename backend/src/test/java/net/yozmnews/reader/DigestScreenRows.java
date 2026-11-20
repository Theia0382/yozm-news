package net.yozmnews.reader;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 도우미 — 다이제스트 화면이 읽는 다이제스트 · 섹션 · 인용 행. 모두 SQL 로 넣는다. 시작한 행은 공급자 openai · 모델 m · 후보 30 을 갖고, 끝난
 * 행은 끝낸 시각을 갖는다 — 보이는 다이제스트(READY)는 이 칸이 있어야 읽힌다(digest-R1). 기간은 그 날짜 전날 15:00Z ~ 그 날짜 15:00Z, 계기는 수동
 * — 같은 날짜에 여럿을 넣을 수 있다.
 */
public final class DigestScreenRows {

	/** 끝난 행의 끝낸 시각 */
	public static final Instant COMPLETED = Instant.parse("2026-11-05T15:05:00Z");

	private final JdbcClient jdbc;

	public DigestScreenRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 다이제스트 · 섹션 · 인용 행을 모두 지운다(섹션 · 인용은 다이제스트를 따라 지워진다) */
	public void clear() {
		this.jdbc.sql("delete from digests").update();
	}

	/**
	 * 다이제스트 행 하나.
	 * @param status SCHEDULED · RUNNING · READY · EMPTY · FAILED
	 * @param failure FAILED 의 실패 코드 — 그 밖에는 null
	 * @return 다이제스트 ID
	 */
	public long digest(long user, String date, String status, @Nullable Integer input, @Nullable Integer output,
			@Nullable String failure) {
		boolean started = !status.equals("SCHEDULED");
		boolean finished = List.of("READY", "EMPTY", "FAILED").contains(status);
		return this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, provider, model,
				    candidate_count, input_tokens, output_tokens, error_code, error_message, created_at, dispatch_token,
				    dispatched_at, started_at, completed_at, version)
				values (:user, cast(:date as date), :periodStart, :periodEnd, :status, 'MANUAL', :provider, :model,
				    :candidates, :input, :output, :failure, :message, timestamptz '2026-11-01T00:00:00Z', gen_random_uuid(),
				    timestamptz '2026-11-01T00:00:00Z', :started, :completed, 0)
				returning id
				""")
			.param("user", user)
			.param("date", date)
			.param("periodStart", Timestamp.from(Instant.parse(LocalDate.parse(date).minusDays(1) + "T15:00:00Z")))
			.param("periodEnd", Timestamp.from(Instant.parse(date + "T15:00:00Z")))
			.param("status", status)
			.param("provider", started ? "openai" : null)
			.param("model", started ? "m" : null)
			.param("candidates", started ? 30 : null)
			.param("input", input)
			.param("output", output)
			.param("failure", failure)
			.param("message", (failure == null) ? null : "운영 로그용 실패 메시지")
			.param("started", started ? Timestamp.from(Instant.parse("2026-11-05T15:00:00Z")) : null)
			.param("completed", finished ? Timestamp.from(COMPLETED) : null)
			.query(Long.class)
			.single();
	}

	/** 섹션 하나 — @return 섹션 ID */
	public long section(long digest, String kind, int position, @Nullable String title, @Nullable String body) {
		return this.jdbc.sql("""
				insert into digest_sections (digest_id, kind, position, title, body)
				values (:digest, :kind, :position, :title, :body) returning id
				""")
			.param("digest", digest)
			.param("kind", kind)
			.param("position", position)
			.param("title", title)
			.param("body", body)
			.query(Long.class)
			.single();
	}

	/** 섹션의 인용(출처 기사) 하나 */
	public void cite(long section, int position, long article) {
		this.jdbc.sql("""
				insert into digest_items (digest_section_id, position, article_id)
				values (:section, :position, :article)
				""")
			.param("section", section)
			.param("position", position)
			.param("article", article)
			.update();
	}

	/** 섹션(항목)의 사건 하나와 그 선정 이유 */
	public void sectionEvent(long section, int position, long event, String reason) {
		this.jdbc.sql("""
				insert into digest_section_events (digest_section_id, position, event_id, selection_reason)
				values (:section, :position, :event, :reason)
				""")
			.param("section", section)
			.param("position", position)
			.param("event", event)
			.param("reason", reason)
			.update();
	}

	/** 그 사용자의 다이제스트 행 수 */
	public long count(long user) {
		return this.jdbc.sql("select count(*) from digests where user_id = :user")
			.param("user", user)
			.query(Long.class)
			.single();
	}

}
