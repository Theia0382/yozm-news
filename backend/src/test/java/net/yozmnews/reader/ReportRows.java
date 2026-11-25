package net.yozmnews.reader;

import java.time.Instant;
import java.time.ZoneOffset;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 독자 화면 시험의 변화 리포트 행 — 리포트 · 섹션 · 인용을 SQL 로 넣는다(생성기는 돌지 않는다). 시작한 행은 공급자 · 모델 · 스냅숏 · 시간대를
 * 함께 넣는다(테이블 제약 — docs/specs/report.md §6).
 */
public final class ReportRows {

	/** 기본 기간 — 서울의 2026-11-16(월) 0 시 ~ 2026-11-23(월) 0 시 */
	public static final Instant WEEK_START = Instant.parse("2026-11-15T15:00:00Z");

	/** 기본 기간의 끝 */
	public static final Instant WEEK_END = Instant.parse("2026-11-22T15:00:00Z");

	private final JdbcClient jdbc;

	public ReportRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 리포트 · 섹션 · 인용을 비운다 */
	public void clear() {
		this.jdbc.sql("delete from reports").update();
	}

	/** 주간 리포트 — 기본 기간, 시작한 행은 시간대 Asia/Seoul */
	public long report(long user, String status, @Nullable String snapshot) {
		return report(user, "WEEK", status, WEEK_START, WEEK_END, snapshot, null);
	}

	/**
	 * 리포트 하나 — 시작한 행(SCHEDULED 밖)은 openai · gpt-5.5-mini · 시간대 Asia/Seoul, READY 는 토큰 120 / 45, FAILED 는
	 * QUOTA_EXHAUSTED
	 */
	public long report(long user, String kind, String status, Instant start, Instant end, @Nullable String snapshot,
			@Nullable Long previous) {
		boolean started = !status.equals("SCHEDULED");
		boolean finished = status.equals("READY") || status.equals("EMPTY") || status.equals("FAILED");
		return this.jdbc.sql("""
				insert into reports (user_id, period_kind, period_start, period_end, status, provider, model, input_tokens,
				                     output_tokens, stats_snapshot, error_code, error_message, created_at, started_at,
				                     completed_at, zone, previous_report_id, dispatch_token, dispatched_at, scheduled_at,
				                     regenerations, version)
				values (:user, :kind, :start, :end, :status, :provider, :model, :input, :output, cast(:snapshot as jsonb),
				        :error, :message, :end, :started, :completed, :zone, :previous, gen_random_uuid(), :end, :end, 0, 1)
				returning id""")
			.param("user", user)
			.param("kind", kind)
			.param("start", start.atOffset(ZoneOffset.UTC))
			.param("end", end.atOffset(ZoneOffset.UTC))
			.param("status", status)
			.param("provider", started ? "openai" : null, java.sql.Types.VARCHAR)
			.param("model", started ? "gpt-5.5-mini" : null, java.sql.Types.VARCHAR)
			.param("input", status.equals("READY") ? 120 : null, java.sql.Types.INTEGER)
			.param("output", status.equals("READY") ? 45 : null, java.sql.Types.INTEGER)
			.param("snapshot", started ? ((snapshot == null) ? "{}" : snapshot) : null, java.sql.Types.VARCHAR)
			.param("error", status.equals("FAILED") ? "QUOTA_EXHAUSTED" : null, java.sql.Types.VARCHAR)
			.param("message", status.equals("FAILED") ? "잔액이 없다" : null, java.sql.Types.VARCHAR)
			.param("started", started ? end.atOffset(ZoneOffset.UTC) : null, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
			.param("completed", finished ? end.plusSeconds(60).atOffset(ZoneOffset.UTC) : null,
					java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
			.param("zone", started ? "Asia/Seoul" : null, java.sql.Types.VARCHAR)
			.param("previous", previous, java.sql.Types.BIGINT)
			.query(Long.class)
			.single();
	}

	/** 섹션 하나 — @return 섹션 ID */
	public long section(long report, String kind, int position, @Nullable String title, String body) {
		return this.jdbc.sql("""
				insert into report_sections (report_id, kind, position, title, body)
				values (:report, :kind, :position, :title, :body) returning id""")
			.param("report", report)
			.param("kind", kind)
			.param("position", position)
			.param("title", title, java.sql.Types.VARCHAR)
			.param("body", body)
			.query(Long.class)
			.single();
	}

	/** 인용 하나 — 스토리라인 또는 사건 */
	public void item(long section, int position, @Nullable Long storyline, @Nullable Long event) {
		this.jdbc.sql("""
				insert into report_items (report_section_id, position, storyline_id, event_id)
				values (:section, :position, :storyline, :event)""")
			.param("section", section)
			.param("position", position)
			.param("storyline", storyline, java.sql.Types.BIGINT)
			.param("event", event, java.sql.Types.BIGINT)
			.update();
	}

}
