package net.yozmnews.storyline.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.storyline.domain.linking.Representation;
import net.yozmnews.storyline.domain.linking.Score;
import net.yozmnews.storyline.domain.storyline.LinkJudgement;
import net.yozmnews.storyline.domain.storyline.LinkedBy;
import net.yozmnews.storyline.domain.storyline.StorylineSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 스토리라인 저장소 — 후보의 조건(모델 버전 · 시각 범위 겹침 · 코사인 하한 · NaN 제외)과 순서, 핵심 말 상위 수와 순서, 표현 쓰기(핵심 말을
 * 통째로 바꾼다), 소속 더하기 · 옮기기 · 지우기, 스토리라인 지우기, 합치기(평평한 가리킴 · 남는 쪽을 지우면 가리키는 행도 · 후보에서 빠짐),
 * 모듈 잠금을 Flyway 가 적용된 실제 PostgreSQL 에서 본다. 모델 버전을 시험마다 따로 써 다른 시험의 행과 겹치지 않게 한다.
 * <p>
 * 지키는 규칙: storyline-R5 · storyline-R6 · storyline-R7 · storyline-R9 · storyline-R13 · storyline-R14 · storyline-R18
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcStorylineRepository.class })
class JdbcStorylineRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	@Autowired
	JdbcStorylineRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void candidatesAreSameModelOverlappingAboveTheFloor() {
		// 정수 성분 — 반 정밀도에서도 코사인이 정확하다: [4,3] 0.8, [3,4] 0.6
		long s1 = open("cand-m", new float[] { 4, 3 }, "2026-11-02T00:00:00Z", "2026-11-03T00:00:00Z",
				Map.of("물가", 1, "금리", 3, "환율", 1));
		long s2 = open("cand-m", new float[] { 3, 4 }, "2026-11-05T00:00:00Z", "2026-11-07T00:00:00Z", Map.of());
		open("cand-other", new float[] { 1, 0 }, "2026-11-02T00:00:00Z", "2026-11-03T00:00:00Z", Map.of());
		open("cand-m", new float[] { 1, 0 }, "2026-11-06T00:00:00Z", "2026-11-07T00:00:00Z", Map.of());
		open("cand-m", new float[] { 0, 0 }, "2026-11-02T00:00:00Z", "2026-11-03T00:00:00Z", Map.of());
		open("cand-m", new float[] { 1, 0 }, "2026-10-29T00:00:00Z", "2026-10-31T23:59:59Z", Map.of());
		Instant from = Instant.parse("2026-11-01T00:00:00Z");
		Instant to = Instant.parse("2026-11-05T00:00:00Z");
		float[] vector = { 1, 0 };

		List<StorylineSnapshot> above = this.repository.candidates("cand-m", vector, from, to, 0.7, 2);
		assertThat(above).extracting(StorylineSnapshot::id).containsExactly(s1);
		assertThat(above.get(0).centroid()).containsExactly(4f, 3f);
		assertThat(above.get(0).firstArticleAt()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
		// 기사 수가 많은 순, 같으면 말 바이트 순(물 U+BB3C < 환 U+D658) — 상위 2개
		assertThat(above.get(0).keyTerms()).containsExactly("금리", "물가");
		// 경계(11-05 에서 시작)는 포함, ID 순. 영 벡터는 하한 −1 이어도 빠진다. 범위 밖(11-06 ~ · ~ 10-31)과 다른 모델 버전도 빠진다
		assertThat(this.repository.candidates("cand-m", vector, from, to, 0.5, 2)).extracting(StorylineSnapshot::id)
			.containsExactly(s1, s2);
		assertThat(this.repository.candidates("cand-m", vector, from, to, -1, 2)).extracting(StorylineSnapshot::id)
			.containsExactly(s1, s2);
	}

	@Test
	void saveRewritesTheRepresentationAndReplacesTheTerms() {
		long id = open("save-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-02T00:00:00Z",
				Map.of("금리", 2, "물가", 1));

		this.repository.save(id, new Representation("save-m", new float[] { 0.75f, 0.25f }, 4,
				Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-04T00:00:00Z"), Map.of("환율", 3), 77),
				NOW);

		assertThat(this.jdbc.sql("""
				select centroid::text as centroid, article_count, representative_event_id,
				       to_char(last_article_at at time zone 'UTC', 'YYYY-MM-DD') as last, updated_at = :now as updated
				from storylines where id = :id
				""").param("id", id).param("now", NOW.atOffset(ZoneOffset.UTC)).query().singleRow())
			.containsEntry("centroid", "[0.75,0.25]")
			.containsEntry("article_count", 4)
			.containsEntry("representative_event_id", 77L)
			.containsEntry("last", "2026-11-04")
			.containsEntry("updated", true);
		assertThat(terms(id)).containsExactlyInAnyOrderEntriesOf(Map.of("환율", 3));
	}

	@Test
	void membershipsAreAddedMovedRemovedAndRead() {
		long a = open("member-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z", Map.of());
		long b = open("member-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z", Map.of());
		long criteria = criteria();
		Score score = new Score(0.93, 0.9, 1.0);
		this.repository.addMember(a, 8102, LinkJudgement.judged(LinkedBy.PAIR, score, criteria, NOW));
		this.repository.addMember(a, 8101, LinkJudgement.judged(LinkedBy.PAIR, score, criteria, NOW));
		this.repository.addMember(b, 8101, LinkJudgement.judged(LinkedBy.RULE, new Score(0.7, 0.8, 0.5), criteria, NOW));

		assertThat(this.repository.storylinesOf(8101)).containsExactly(a, b);
		assertThat(this.repository.members(List.of(b, a))).containsExactly(Map.entry(a, List.of(8101L, 8102L)),
				Map.entry(b, List.of(8101L)));
		assertThat(this.repository.affiliated(List.of(8101L, 8102L, 8199L))).containsExactlyInAnyOrder(8101L, 8102L);
		assertThat(member(a, 8102)).containsEntry("linked_by", "PAIR")
			.containsEntry("score", 0.93)
			.containsEntry("cosine", 0.9)
			.containsEntry("key_term_overlap", 1.0)
			.containsEntry("criteria_id", criteria);

		this.repository.moveMember(a, 8102, 8109, NOW.plusSeconds(60));
		assertThat(member(a, 8109)).containsEntry("linked_by", "MERGE")
			.containsEntry("score", null)
			.containsEntry("cosine", null)
			.containsEntry("key_term_overlap", null)
			.containsEntry("criteria_id", null);
		assertThat(this.repository.storylinesOf(8102)).isEmpty();

		this.repository.removeMember(b, 8101);
		assertThat(this.repository.storylinesOf(8101)).containsExactly(a);
		assertThat(this.repository.members(List.of(b))).isEmpty();
		assertThat(this.repository.members(List.of())).isEmpty();
		assertThat(this.repository.affiliated(List.of())).isEmpty();
	}

	@Test
	void deletingAStorylineTakesItsTermsAndMemberships() {
		long id = open("delete-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				Map.of("금리", 1));
		this.repository.addMember(id, 8201, LinkJudgement.merged(NOW));

		this.repository.delete(id);

		assertThat(count("select count(*) from storylines where id = " + id)).isZero();
		assertThat(count("select count(*) from storyline_key_terms where storyline_id = " + id)).isZero();
		assertThat(count("select count(*) from storyline_members where storyline_id = " + id)).isZero();
	}

	@Test
	void mergePointsAtTheSurvivorAndDropsMembers() {
		long l1 = open("merge-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				Map.of("금리", 2));
		long l2 = open("merge-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				Map.of("금리", 2));
		long l3 = open("merge-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				Map.of("금리", 2));
		for (long storyline : List.of(l1, l2, l3)) {
			this.repository.addMember(storyline, 8301, LinkJudgement.merged(NOW));
			this.repository.addMember(storyline, 8302, LinkJudgement.merged(NOW));
		}
		Instant first = Instant.parse("2026-11-10T01:00:00Z");
		Instant second = Instant.parse("2026-11-10T02:00:00Z");

		this.repository.merge(l3, l2, first);
		this.repository.merge(l2, l1, second);

		// 가리킴은 평평하다 — l3 도 l1 을 가리키고, l3 의 합친 시각은 처음 그대로다
		assertThat(merge(l2)).containsEntry("merged_into", l1).containsEntry("merged_at", "2026-11-10T02:00:00Z");
		assertThat(merge(l3)).containsEntry("merged_into", l1).containsEntry("merged_at", "2026-11-10T01:00:00Z");
		assertThat(merge(l1)).containsEntry("merged_into", null).containsEntry("merged_at", null);
		for (long gone : List.of(l2, l3)) {
			assertThat(count("select count(*) from storyline_members where storyline_id = " + gone)).isZero();
			assertThat(count("select count(*) from storyline_key_terms where storyline_id = " + gone)).isZero();
		}
		assertThat(this.repository.members(List.of(l1, l2, l3))).containsExactly(Map.entry(l1, List.of(8301L, 8302L)));
		assertThat(terms(l1)).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 2));
		assertThat(this.repository.storylinesOf(8301)).containsExactly(l1);
	}

	@Test
	void deletingTheSurvivorTakesTheMergedRows() {
		long l1 = open("merge-delete-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				Map.of());
		long l2 = open("merge-delete-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				Map.of());
		long l3 = open("merge-delete-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				Map.of());
		long other = open("merge-delete-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				Map.of());
		this.repository.merge(l3, l2, NOW);
		this.repository.merge(l2, l1, NOW);

		this.repository.delete(l1);

		assertThat(count("select count(*) from storylines where model_version = 'merge-delete-m'")).isEqualTo(1);
		assertThat(count("select count(*) from storylines where id = " + other)).isEqualTo(1);
	}

	@Test
	void candidatesSkipMergedStorylines() {
		long l1 = open("merge-cand-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-02T00:00:00Z",
				Map.of());
		long l2 = open("merge-cand-m", new float[] { 1, 0 }, "2026-11-01T00:00:00Z", "2026-11-02T00:00:00Z",
				Map.of());
		Instant from = Instant.parse("2026-10-31T00:00:00Z");
		Instant to = Instant.parse("2026-11-03T00:00:00Z");
		assertThat(this.repository.candidates("merge-cand-m", new float[] { 1, 0 }, from, to, 0.5, 2))
			.extracting(StorylineSnapshot::id)
			.containsExactly(l1, l2);

		this.repository.merge(l2, l1, NOW);

		assertThat(this.repository.candidates("merge-cand-m", new float[] { 1, 0 }, from, to, 0.5, 2))
			.extracting(StorylineSnapshot::id)
			.containsExactly(l1);
	}

	@Test
	void lockLinkingHoldsTheModuleAdvisoryLock() {
		this.repository.lockLinking();

		assertThat(this.jdbc.sql("""
				select classid::bigint as namespace, objid::bigint as key, objsubid::int as objsubid from pg_locks
				where locktype = 'advisory' and pid = pg_backend_pid()
				""").query().singleRow()).containsEntry("namespace", 1_398_035_033L)
			.containsEntry("key", 0L)
			.containsEntry("objsubid", 2);
	}

	private long open(String model, float[] centroid, String first, String last, Map<String, Integer> terms) {
		return this.repository.open(new Representation(model, centroid, 3, Instant.parse(first), Instant.parse(last),
				terms, 1), NOW);
	}

	private long criteria() {
		return this.jdbc.sql("""
				insert into storyline_linking_criteria (window_seconds, threshold, created_at) values (1209600, 0.65, now())
				on conflict (window_seconds, threshold) do update set created_at = excluded.created_at
				returning id
				""").query(Long.class).single();
	}

	private Map<String, Integer> terms(long storyline) {
		return this.jdbc.sql("select term, article_count from storyline_key_terms where storyline_id = :id")
			.param("id", storyline)
			.query((rs, row) -> Map.entry(rs.getString("term"), rs.getInt("article_count")))
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private Map<String, Object> member(long storyline, long event) {
		return this.jdbc.sql("""
				select linked_by, score, cosine, key_term_overlap, criteria_id from storyline_members
				where storyline_id = :storyline and event_id = :event
				""").param("storyline", storyline).param("event", event).query().singleRow();
	}

	private Map<String, Object> merge(long storyline) {
		return this.jdbc.sql("""
				select merged_into, to_char(merged_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as merged_at
				from storylines where id = :id
				""").param("id", storyline).query().singleRow();
	}

	private long count(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
