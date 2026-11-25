package net.yozmnews.evaluation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import javax.sql.DataSource;

import net.yozmnews.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판정 원본 표 둘의 이름을 스토리라인으로 바꾸는 마이그레이션(V86) — 실제 PostgreSQL 의 별도 스키마에 V85 까지만 올려 옛 이름의 표에
 * 스토리라인 소속 · 관계 판정 원본과 사건 소속 판정 원본을 넣고, 끝까지 올린 뒤 표 · 칸 · 제약 · 인덱스 · 시퀀스가 새 이름만 갖고 행이
 * 그대로인지, 물은 후보의 ID 키가 바뀌었는지 본다. 앱이 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: evaluation-R49 · evaluation-R52
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class StorylineJudgementNamesMigrationTests {

	private static final String SCHEMA = "v85_storyline_judgement_names";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void theJudgementTablesTakeStorylineNamesAndKeepTheirRows() {
		flyway("85").migrate();
		// 후보 41 은 붙이기 꼴(스토리라인), 42 는 짝 꼴(이웃 사건) — 이 모듈에는 둘 다 후보 ID 다
		long membership = this.jdbc.sql("insert into " + SCHEMA + ".flow_membership_judgements (article_id, evaluator, "
				+ "model_version, wording_version, candidates, raw_response, input_tokens, judged_at) values (500, 'jev', "
				+ "'jev-1', 'matter-1', cast(:candidates as jsonb), cast('{}' as jsonb), 12, now()) returning id")
			.param("candidates", "[{\"flowId\": 41, \"articleIds\": [501, 502], \"probability\": 0.83}, "
					+ "{\"flowId\": 42, \"articleIds\": [503], \"probability\": 0.07}]")
			.query(Long.class)
			.single();
		long relation = this.jdbc.sql("insert into " + SCHEMA + ".flow_relation_judgements (first_flow_id, second_flow_id, "
				+ "described, evaluator, model_version, wording_version, raw_response, input_tokens, judged_at) values (7, 9, "
				+ "cast('{\"first\": [], \"second\": []}' as jsonb), 'jev', 'jev-1', 'relation-2', cast('{}' as jsonb), 30, "
				+ "now()) returning id")
			.query(Long.class)
			.single();
		this.jdbc.sql("insert into " + SCHEMA + ".article_event_judgements (article_id, evaluator, model_version, "
				+ "wording_version, candidates, raw_response, input_tokens, judged_at) values (600, 'jev', 'jev-1', "
				+ "'event-1', cast('[{\"eventId\": 12, \"articleIds\": [601], \"probability\": 0.5}]' as jsonb), "
				+ "cast('{}' as jsonb), 5, now())")
			.update();

		flyway(null).migrate();

		assertThat(this.jdbc.sql("select table_name from information_schema.tables where table_schema = :schema "
				+ "and table_name like any (array['flow%', 'storyline_%_judgements']) order by table_name")
			.param("schema", SCHEMA)
			.query(String.class)
			.list()).containsExactly("storyline_membership_judgements", "storyline_relation_judgements");
		assertThat(this.jdbc.sql("select article_id, candidates::text from " + SCHEMA
				+ ".storyline_membership_judgements where id = :id").param("id", membership).query().singleRow())
			.containsEntry("article_id", 500L)
			.containsEntry("candidates", "[{\"articleIds\": [501, 502], \"candidateId\": 41, \"probability\": 0.83}, "
					+ "{\"articleIds\": [503], \"candidateId\": 42, \"probability\": 0.07}]");
		assertThat(this.jdbc.sql("select first_storyline_id, second_storyline_id from " + SCHEMA
				+ ".storyline_relation_judgements where id = :id").param("id", relation).query().singleRow())
			.containsEntry("first_storyline_id", 7L)
			.containsEntry("second_storyline_id", 9L);
		// 대조 — 사건 소속 판정 원본은 이름도 키도 그대로다
		assertThat(this.jdbc.sql("select candidates::text from " + SCHEMA + ".article_event_judgements")
			.query(String.class)
			.single()).isEqualTo("[{\"eventId\": 12, \"articleIds\": [601], \"probability\": 0.5}]");

		// 이름 지은 제약도, PostgreSQL 이 표 · 칸 이름으로 지은 제약(기본 키 · NOT NULL · CHECK)도 옛 이름이 남지 않는다
		assertThat(names("select conname from pg_constraint where connamespace = cast(:schema as regnamespace) "
				+ "and conname like 'flow%'")).isEmpty();
		assertThat(names("select conname from pg_constraint where connamespace = cast(:schema as regnamespace) "
				+ "and conname like 'storyline_%_judgements%'")).contains("storyline_membership_judgements_pkey",
						"storyline_membership_judgements_candidates_not_null", "storyline_relation_judgements_pkey",
						"storyline_relation_judgements_distinct_storylines",
						"storyline_relation_judgements_first_storyline_id_not_null",
						"storyline_relation_judgements_input_tokens_check");
		assertThat(names("select indexname from pg_indexes where schemaname = :schema "
				+ "and indexname like any (array['flow%', 'storyline_%_judgements%'])")).containsExactlyInAnyOrder(
						"storyline_membership_judgements_pkey", "storyline_membership_judgements_article_idx",
						"storyline_membership_judgements_judged_idx", "storyline_relation_judgements_pkey",
						"storyline_relation_judgements_judged_idx");
		assertThat(names("select sequencename from pg_sequences where schemaname = :schema "
				+ "and sequencename like any (array['flow%', 'storyline_%_judgements%'])")).containsExactlyInAnyOrder(
						"storyline_membership_judgements_id_seq", "storyline_relation_judgements_id_seq");

		// 이름을 바꾼 identity 가 이어서 번호를 낸다
		long next = this.jdbc.sql("insert into " + SCHEMA + ".storyline_membership_judgements (article_id, evaluator, "
				+ "model_version, wording_version, candidates, raw_response, input_tokens, judged_at) values (510, 'jev', "
				+ "'jev-1', 'matter-1', cast('[]' as jsonb), cast('{}' as jsonb), 1, now()) returning id")
			.query(Long.class)
			.single();
		assertThat(next).isGreaterThan(membership);
		// 이름을 바꾼 제약이 같은 스토리라인 쌍을 막는다
		assertThatThrownBy(() -> this.jdbc.sql("insert into " + SCHEMA + ".storyline_relation_judgements "
				+ "(first_storyline_id, second_storyline_id, described, evaluator, model_version, wording_version, "
				+ "raw_response, input_tokens, judged_at) values (8, 8, cast('{}' as jsonb), 'jev', 'jev-1', "
				+ "'relation-2', cast('{}' as jsonb), 1, now())")
			.update()).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("storyline_relation_judgements_distinct_storylines");
	}

	private List<String> names(String sql) {
		return this.jdbc.sql(sql).param("schema", SCHEMA).query(String.class).list();
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
