package net.yozmnews.reader;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.event.EventArticles;
import net.yozmnews.storyline.StorylineRows;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 도우미 — 독자 화면 시험의 기사 · 사건 · 배정 · 스토리라인 · 관계 · 다이제스트 행. 모두 SQL 로 넣는다(스토리라인 조회 시험과 같은 방식). 시험
 * 문장은 지어낸 것이다.
 */
public final class ReaderRows {

	private final JdbcClient jdbc;

	private final List<Long> evaluated = new ArrayList<>();

	private long assignmentCriteria;

	public ReaderRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 스토리라인 · 관계 · 사건 묶기 · 기사 · 다이제스트 행과 이 도우미가 넣은 평가 행을 비운다 */
	public void clear() {
		this.jdbc.sql("delete from storyline_relations").update();
		StorylineRows.clear(this.jdbc);
		this.jdbc.sql("delete from digests").update();
		if (!this.evaluated.isEmpty()) {
			this.jdbc.sql("delete from article_evaluations where article_id = any(:ids)")
				.param("ids", this.evaluated.toArray(Long[]::new))
				.update();
			this.evaluated.clear();
		}
		this.assignmentCriteria = 0;
	}

	/** 기사 하나 — 소스 1 · 피드 11 의 엔트리 하나, 이름 있는 피드가 없어 출처 이름은 비어 있다 */
	public long article(String title) {
		return EventArticles.insert(this.jdbc, title, "요약문 원문");
	}

	/** 기사 하나와 그 중요도(1 ~ 5) — 큰 판정의 재료 */
	public long article(String title, double importance) {
		long article = article(title);
		this.jdbc.sql("""
				insert into article_evaluations (article_id, tag_set_id, judgement_id, format_key, format_probability,
				    format_distribution, importance, importance_distribution, evaluated_at)
				values (:article, (select id from tag_sets order by number desc limit 1), :article, 'straight', 1.0,
				    '{}', :importance, '{}', '2026-11-05T00:00:00Z')
				""").param("article", article).param("importance", importance).update();
		this.evaluated.add(article);
		return article;
	}

	/** 사건 하나 — 첫 · 마지막 기사 시각과 기사 수 */
	public long event(int articles, String first, String last) {
		return StorylineRows.event(this.jdbc, "OPEN", "model-a", "[1,0]", articles, first, last, Map.of());
	}

	/** 기사를 사건에 배정한다 — 발행 시각은 기간 안 기사를 고르는 시각이다 */
	public void assign(long article, long event, String published) {
		if (this.assignmentCriteria == 0) {
			this.assignmentCriteria = this.jdbc.sql("""
					insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
					values (259200, 0.8, 0.55, 0.4, 0.5, now()) returning id
					""").query(Long.class).single();
		}
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, score, decided_by, opened, criteria_id,
				                            assigned_at)
				values (:article, :event, :published, 0.9, 'FORMULA', false, :criteria, '2026-11-06T00:00:00Z')
				""")
			.param("article", article)
			.param("event", event)
			.param("published", Instant.parse(published).atOffset(ZoneOffset.UTC))
			.param("criteria", this.assignmentCriteria)
			.update();
	}

	/** 스토리라인 하나 — 저장된 표현의 첫 · 마지막 기사 시각과 대표 사건 */
	public long storyline(String first, String last, long representative) {
		return this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('model-a', cast('[1,0]' as halfvec), 1, :first, :last, :representative, '2026-11-06T00:00:00Z',
				        '2026-11-06T00:00:00Z')
				returning id
				""")
			.param("first", Instant.parse(first).atOffset(ZoneOffset.UTC))
			.param("last", Instant.parse(last).atOffset(ZoneOffset.UTC))
			.param("representative", representative)
			.query(Long.class)
			.single();
	}

	/** 사건을 스토리라인의 직접 소속으로 넣는다 */
	public void member(long storyline, long event) {
		this.jdbc.sql("""
				insert into storyline_members (storyline_id, event_id, linked_by, linked_at)
				values (:storyline, :event, 'MERGE', '2026-11-06T00:00:00Z')
				""").param("storyline", storyline).param("event", event).update();
	}

	/** 스토리라인을 다른 스토리라인에 합쳐진 것으로 바꾼다 */
	public void merge(long merged, long survivor) {
		this.jdbc.sql("delete from storyline_members where storyline_id = :id").param("id", merged).update();
		this.jdbc.sql("update storylines set merged_into = :into, merged_at = now() where id = :id")
			.param("into", survivor)
			.param("id", merged)
			.update();
	}

	/** 관계 하나 — 포함(source 가 target 을 포함) 또는 이어짐(target 이 source 에 이어짐) */
	public void relate(String kind, long source, long target) {
		long criteria = this.jdbc.sql("""
				insert into storyline_relation_criteria (probability, one_matter, min_events, candidate_score,
				    window_seconds, created_at)
				values (0.6, 0.2, 3, 0.88, 604800, now())
				on conflict (probability, one_matter, min_events, candidate_score, window_seconds)
				do update set probability = excluded.probability
				returning id
				""").query(Long.class).single();
		this.jdbc.sql("""
				insert into storyline_relations (kind, source_id, target_id, probability, judgement_id, criteria_id,
				    linked_at)
				values (:kind, :source, :target, 0.7, 1, :criteria, '2026-11-06T00:00:00Z')
				""")
			.param("kind", kind)
			.param("source", source)
			.param("target", target)
			.param("criteria", criteria)
			.update();
	}

	/** 사용자의 READY 다이제스트 하나 — 그 날짜 */
	public long digest(long user, String date) {
		return this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, created_at,
				    started_at, completed_at, version)
				values (:user, cast(:date as date), cast(:date as date) - interval '1 day', cast(:date as date), 'READY',
				    'SCHEDULED', now(), now(), now(), 0)
				returning id
				""").param("user", user).param("date", date).query(Long.class).single();
	}

	/** 다이제스트가 기사를 인용한다 */
	public void cite(long digest, long article) {
		long section = this.jdbc.sql("""
				insert into digest_sections (digest_id, kind, position, title, body)
				values (:digest, 'MORE_HEADLINES', (select count(*) from digest_sections where digest_id = :digest), null,
				    null)
				returning id
				""").param("digest", digest).query(Long.class).single();
		this.jdbc.sql("""
				insert into digest_items (digest_section_id, position, article_id, note, selection_reason)
				values (:section, 0, :article, null, 'SUBSCRIBED')
				""").param("section", section).param("article", article).update();
	}

}
