package net.yozmnews.storyline.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 조각 합치기의 칸을 더하는 마이그레이션(V63) — 실제 PostgreSQL 의 별도 스키마에 V62 까지만 올려 옛 관계 기준 · 합쳐진 스토리라인 ·
 * 소속 행을 넣고, 끝까지 올린 뒤 옛 기준의 합치기 확률 기준이 1.0(조각을 합치지 않던 기준)이고 옛 합쳐진 행 · 소속에 합치기 근거가
 * 없는지 본다. 앱이 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: storyline-R17 · storyline-R18
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class FragmentMergeMigrationTests {

	private static final String SCHEMA = "v62_fragment_merge";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void earlierCriteriaNeverMergeAndEarlierRowsCarryNoMergeBasis() {
		flyway("62").migrate();
		long criteria = this.jdbc.sql("insert into " + SCHEMA + ".storyline_relation_criteria (probability, one_matter, "
				+ "min_events, candidate_score, window_seconds, created_at) values (0.6, 0.2, 3, 0.88, 1209600, now()) "
				+ "returning id").query(Long.class).single();
		long survivor = storyline(null);
		long merged = storyline(survivor);
		long linking = this.jdbc.sql("insert into " + SCHEMA + ".storyline_linking_criteria (window_seconds, lower, upper, "
				+ "probability, created_at) values (1209600, 0.84, 0.88, 0.7, now()) returning id")
			.query(Long.class)
			.single();
		this.jdbc.sql("insert into " + SCHEMA + ".storyline_members (storyline_id, event_id, linked_by, score, cosine, "
				+ "key_term_overlap, criteria_id, linked_at) values (:storyline, 5, 'RULE', 0.9, 0.9, 0.5, :criteria, now())")
			.param("storyline", survivor)
			.param("criteria", linking)
			.update();

		flyway(null).migrate();

		assertThat(this.jdbc.sql("select probability, merge_probability from " + SCHEMA
				+ ".storyline_relation_criteria where id = :id").param("id", criteria).query().singleRow())
			.containsEntry("probability", 0.6)
			.containsEntry("merge_probability", 1.0);
		assertThat(this.jdbc.sql("select merged_into, merge_judgement_id, merge_criteria_id from " + SCHEMA
				+ ".storylines where id = :id").param("id", merged).query().singleRow())
			.containsEntry("merged_into", survivor)
			.containsEntry("merge_judgement_id", null)
			.containsEntry("merge_criteria_id", null);
		assertThat(this.jdbc.sql("select linked_by, merged_from from " + SCHEMA
				+ ".storyline_members where storyline_id = :id").param("id", survivor).query().singleRow())
			.containsEntry("linked_by", "RULE")
			.containsEntry("merged_from", null);
		// 같은 값에 합치기 확률 기준만 다른 기준은 다른 이력 행으로 들어간다
		this.jdbc.sql("insert into " + SCHEMA + ".storyline_relation_criteria (probability, one_matter, min_events, "
				+ "candidate_score, window_seconds, merge_probability, created_at) "
				+ "values (0.6, 0.2, 3, 0.88, 1209600, 0.6, now())").update();
		assertThat(this.jdbc.sql("select count(*) from " + SCHEMA + ".storyline_relation_criteria")
			.query(Long.class)
			.single()).isEqualTo(2);
	}

	/** 스토리라인 하나 — mergedInto 가 있으면 그 스토리라인에 합쳐진 것 */
	private long storyline(@Nullable Long mergedInto) {
		return this.jdbc.sql("insert into " + SCHEMA + ".storylines (model_version, centroid, article_count, "
				+ "first_article_at, last_article_at, representative_event_id, created_at, updated_at, merged_into, merged_at) "
				+ "values ('m', cast('[1,0]' as public.halfvec), 1, now(), now(), 1, now(), now(), :into, "
				+ "case when cast(:into as bigint) is null then null else now() end) returning id")
			.param("into", mergedInto, java.sql.Types.BIGINT)
			.query(Long.class)
			.single();
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
