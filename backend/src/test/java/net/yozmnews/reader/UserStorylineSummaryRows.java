package net.yozmnews.reader;

import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 도우미 — 사용자 스토리라인과 그 정리 · 시도 기록을 SQL 로 직접 넣는다(정리 작업 · 생성기를 거치지 않는다). 사용자를 지우면 함께 지워진다.
 */
public final class UserStorylineSummaryRows {

	private final JdbcClient jdbc;

	public UserStorylineSummaryRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 추적 대상이 그 스토리라인들인 사용자 스토리라인 — 추적 중이 아니다. @return 사용자 스토리라인 ID */
	public long userStoryline(long user, long... members) {
		long id = this.jdbc
			.sql("insert into user_storylines (user_id, created_at) values (:user, now()) returning id")
			.param("user", user)
			.query(Long.class)
			.single();
		for (long member : members) {
			this.jdbc
				.sql("insert into user_storyline_members (user_storyline_id, storyline_id) values (:id, :storyline)")
				.param("id", id)
				.param("storyline", member)
				.update();
		}
		return id;
	}

	/** 그 사용자 스토리라인을 추적 중으로 둔다 */
	public void track(long user, long userStoryline) {
		this.jdbc.sql("""
				insert into user_storyline_subscriptions (user_id, user_storyline_id, created_at)
				values (:user, :id, now())
				""").param("user", user).param("id", userStoryline).update();
	}

	/**
	 * 사용자 스토리라인 전체의 정리 — 글은 "{이름} 제목" · "{이름} 요약" · "{이름} 최근", 마지막 정리는 anthropic · claude-sonnet-5-5 의 1,200 / 300
	 * 토큰, 시도 기록은 호출 2 · 합 1,500 / 340 이다. 추적 대상 꼭대기마다
	 * 글 없는 꼭대기 행을 함께 둔다({@link #top} 이 글을 넣는다)
	 */
	public void summarize(long userStoryline, String name, Instant updatedAt) {
		this.jdbc.sql("""
				insert into user_storyline_summaries (user_storyline_id, title, summary, recent, provider, model,
				    input_tokens, output_tokens, created_at, updated_at, version)
				values (:id, :title, :summary, :recent, 'anthropic', 'claude-sonnet-5-5', 1200, 300, :created, :updated, 0)
				""")
			.param("id", userStoryline)
			.param("title", name + " 제목")
			.param("summary", name + " 요약")
			.param("recent", name + " 최근")
			.param("created", Instant.parse("2026-11-01T00:00:00Z").atOffset(ZoneOffset.UTC))
			.param("updated", updatedAt.atOffset(ZoneOffset.UTC))
			.update();
		this.jdbc.sql("""
				insert into user_storyline_summary_attempts (user_storyline_id, calls, total_input_tokens,
				    total_output_tokens, failures)
				values (:id, 2, 1500, 340, 0)
				""").param("id", userStoryline).update();
		// 정리에는 추적 대상 꼭대기마다 글 행이 있다 — 처음에는 글 없음으로 두고 top 이 덮어쓴다
		this.jdbc.sql("""
				insert into user_storyline_top_summaries (user_storyline_id, storyline_id, title, summary, recent)
				select user_storyline_id, storyline_id, '', '', '' from user_storyline_members
				where user_storyline_id = :id
				""").param("id", userStoryline).update();
	}

	/** 꼭대기 하나의 글 — "{이름} 제목" · "{이름} 요약" · "{이름} 최근". 이름이 빈 문자열이면 글 없는 꼭대기다 */
	public void top(long userStoryline, long storyline, String name) {
		this.jdbc.sql("""
				insert into user_storyline_top_summaries (user_storyline_id, storyline_id, title, summary, recent)
				values (:id, :storyline, :title, :summary, :recent)
				on conflict (user_storyline_id, storyline_id) do update
				set title = excluded.title, summary = excluded.summary, recent = excluded.recent
				""")
			.param("id", userStoryline)
			.param("storyline", storyline)
			.param("title", name.isEmpty() ? "" : name + " 제목")
			.param("summary", name.isEmpty() ? "" : name + " 요약")
			.param("recent", name.isEmpty() ? "" : name + " 최근")
			.update();
	}

}
