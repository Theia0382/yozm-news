package net.yozmnews.reader;

import java.time.Instant;
import java.time.ZoneOffset;

import net.yozmnews.event.EventArticles;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 관심사 히스토리 시험의 행 — 평가가 있는 기사(주제 하나) · 다이제스트 인용 · 좋아요 / 싫어요 이력 · 주제 구독 이력. 시험이 SQL 로 직접 넣는다.
 */
public final class InterestRows {

	private final JdbcClient jdbc;

	public InterestRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 이 도우미가 넣는 행을 비운다 — 사용자를 지우면 선호 이력은 함께 지워진다 */
	public void clear() {
		this.jdbc.sql("delete from digests").update();
		this.jdbc.sql("delete from feedback_events").update();
		this.jdbc.sql("delete from article_tags").update();
		this.jdbc.sql("delete from article_evaluations").update();
		EventArticles.clear(this.jdbc);
	}

	/** 활성 버전으로 평가한 기사 하나 — 주제 중분류 하나(확률 0.9) */
	public long article(String title, String topic) {
		long article = EventArticles.insert(this.jdbc, title, "요약", 1, 11);
		long evaluation = this.jdbc.sql("""
				insert into article_evaluations (article_id, tag_set_id, judgement_id, format_key, format_probability,
				    format_distribution, importance, importance_distribution, evaluated_at)
				values (:article, (select id from tag_sets where active), :article, 'straight', 1.0, '{}', 3.0, '{}',
				    :at)
				returning id
				""")
			.param("article", article)
			.param("at", Instant.parse("2026-01-01T00:00:00Z").atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
		this.jdbc.sql("""
				insert into article_tags (evaluation_id, article_id, tag_kind, tag_key, probability)
				values (:evaluation, :article, 'TOPIC', :topic, 0.9)
				""").param("evaluation", evaluation).param("article", article).param("topic", topic).update();
		return article;
	}

	/** 그 사용자의 READY 다이제스트 하나가 기사들을 인용한다 — 끝낸 시각 */
	public void cite(long user, Instant completedAt, long... articles) {
		long digest = this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, created_at,
				    dispatch_token, dispatched_at, started_at, completed_at, version)
				values (:user, cast(:at as date), cast(:at as date) - interval '1 day', cast(:at as date), 'READY',
				    'MANUAL', :at, gen_random_uuid(), :at, :at, :at, 0)
				returning id
				""").param("user", user).param("at", completedAt.atOffset(ZoneOffset.UTC)).query(Long.class).single();
		for (int i = 0; i < articles.length; i++) {
			long section = this.jdbc.sql("""
					insert into digest_sections (digest_id, kind, position, title, body)
					values (:digest, 'MORE_HEADLINES', :position, null, null)
					returning id
					""").param("digest", digest).param("position", i).query(Long.class).single();
			this.jdbc.sql("""
					insert into digest_items (digest_section_id, position, article_id, note, selection_reason)
					values (:section, 0, :article, null, 'SUBSCRIBED')
					""").param("section", section).param("article", articles[i]).update();
		}
	}

	/** 좋아요 / 싫어요 이력 한 행 */
	public void react(long user, long article, String value, Instant at) {
		this.jdbc.sql("""
				insert into feedback_events (user_id, article_id, value, digest_id, occurred_at)
				values (:user, :article, :value, 1, :at)
				""")
			.param("user", user)
			.param("article", article)
			.param("value", value)
			.param("at", at.atOffset(ZoneOffset.UTC))
			.update();
	}

	/** 사용자가 직접 바꾼 주제 구독 이력 한 행 — 활성 버전의 키 */
	public void subscribe(long user, String topic, String action, Instant at) {
		this.jdbc.sql("""
				insert into user_tag_preference_events (user_id, cause, tag_set_id, tag_kind, tag_key, mode, action,
				    occurred_at)
				values (:user, 'USER', (select id from tag_sets where active), 'TOPIC', :topic, 'SUBSCRIBE', :action,
				    :at)
				""")
			.param("user", user)
			.param("topic", topic)
			.param("action", action)
			.param("at", at.atOffset(ZoneOffset.UTC))
			.update();
	}

}
