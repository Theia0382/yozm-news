package net.yozmnews.evaluation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 태그 판정 원본으로 옮기는 마이그레이션(V26) — 실제 PostgreSQL 의 별도 스키마에 V25 까지만 올려 평가 행을 넣고, 끝까지 올린 뒤 평가마다
 * 평가 ID 와 같은 ID 의 판정 원본이 옛 값으로 생겼는지, 평가 행이 그 ID 만 가리키는지, 다음 판정 원본의 ID 가 옮긴 ID 뒤부터인지 본다. 앱이
 * 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: evaluation-R41 · tag-R15
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ArticleTagJudgementsMigrationTests {

	private static final String SCHEMA = "v26_tag_judgements";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void movesEachEvaluationsRawResponseToAJudgementWithTheSameId() {
		flyway("25").migrate();
		long version = this.jdbc.sql("insert into " + SCHEMA + ".tag_sets (number, active, topic_template, "
				+ "region_template, format_question, importance_question, created_by, created_at) "
				+ "values (1, true, '{description}', '{description}', '형식', '중요도', 'SYSTEM', now()) returning id")
			.query(Long.class)
			.single();
		long jev = evaluation(42, version, "jev", "jev-1.14.0", "{\"answers\": [1, 2]}", 3_245,
				"2026-11-02T00:00:00Z");
		long fake = evaluation(43, version, "fake", "fake-rules-2", "{\"evaluator\": \"fake\"}", 7,
				"2026-11-03T12:30:00Z");

		flyway(null).migrate();

		assertThat(this.jdbc.sql("select id, article_id, evaluator, model_version, raw_response::text as raw_response, "
				+ "input_tokens, to_char(judged_at at time zone 'UTC', 'YYYY-MM-DD HH24:MI:SS') as judged_at from "
				+ SCHEMA + ".article_tag_judgements order by id")
			.query()
			.listOfRows()).containsExactly(
					Map.of("id", jev, "article_id", 42L, "evaluator", "jev", "model_version", "jev-1.14.0",
							"raw_response", "{\"answers\": [1, 2]}", "input_tokens", 3_245, "judged_at",
							"2026-11-02 00:00:00"),
					Map.of("id", fake, "article_id", 43L, "evaluator", "fake", "model_version", "fake-rules-2",
							"raw_response", "{\"evaluator\": \"fake\"}", "input_tokens", 7, "judged_at",
							"2026-11-03 12:30:00"));
		assertThat(this.jdbc.sql("select id, judgement_id from " + SCHEMA + ".article_evaluations order by id")
			.query()
			.listOfRows()).containsExactly(Map.of("id", jev, "judgement_id", jev), Map.of("id", fake, "judgement_id", fake));
		assertThat(this.jdbc.sql("""
				select column_name from information_schema.columns
				where table_schema = :schema and table_name = 'article_evaluations'
				""").param("schema", SCHEMA).query(String.class).list()).contains("judgement_id")
			.doesNotContain("evaluator", "model_version", "raw_response", "input_tokens");
		// 다음 판정 원본은 옮긴 ID 의 최댓값 뒤부터다
		long next = this.jdbc.sql("insert into " + SCHEMA + ".article_tag_judgements (article_id, evaluator, "
				+ "model_version, raw_response, input_tokens, judged_at) values (44, 'jev', 'jev-1', '{}', 1, now()) "
				+ "returning id")
			.query(Long.class)
			.single();
		assertThat(next).isEqualTo(fake + 1);
	}

	@Test
	void anEmptyDatabaseStartsTheJudgementsAtOne() {
		flyway(null).migrate();

		assertThat(this.jdbc.sql("insert into " + SCHEMA + ".article_tag_judgements (article_id, evaluator, "
				+ "model_version, raw_response, input_tokens, judged_at) values (44, 'jev', 'jev-1', '{}', 1, now()) "
				+ "returning id")
			.query(Long.class)
			.single()).isEqualTo(1L);
		assertThat(this.jdbc.sql("""
				select constraint_type from information_schema.table_constraints
				where table_schema = :schema and table_name = 'article_evaluations'
				  and constraint_name = 'article_evaluations_judgement_key'
				""").param("schema", SCHEMA).query(String.class).list()).isEqualTo(List.of("UNIQUE"));
	}

	private long evaluation(long article, long version, String evaluator, String model, String raw, int tokens,
			String evaluatedAt) {
		return this.jdbc.sql("insert into " + SCHEMA + ".article_evaluations (article_id, tag_set_id, evaluator, "
				+ "model_version, format_key, format_probability, format_distribution, importance, "
				+ "importance_distribution, raw_response, input_tokens, evaluated_at) values (:article, :version, "
				+ ":evaluator, :model, 'straight', 0.5, '{}', 3.0, '{}', cast(:raw as jsonb), :tokens, "
				+ "cast(:at as timestamptz)) returning id")
			.param("article", article)
			.param("version", version)
			.param("evaluator", evaluator)
			.param("model", model)
			.param("raw", raw)
			.param("tokens", tokens)
			.param("at", evaluatedAt)
			.query(Long.class)
			.single();
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
