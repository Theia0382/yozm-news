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
 * 잇기 기준을 띠로 바꾸는 마이그레이션(V49) — 실제 PostgreSQL 의 별도 스키마에 V48 까지만 올려 옛 기준 이력 · 소속 · 대기 행을 넣고, 끝까지
 * 올린 뒤 옛 기준이 띠 없는 기준(아래 = 위 = 옛 기준 점수, 확률 기준 없음)으로 읽히고 옛 소속 · 대기 행이 그대로인지 본다. 앱이 쓰는
 * 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: storyline-R14 · storyline-R36
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class LinkingBandMigrationTests {

	private static final String SCHEMA = "v47_linking_band";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void anOldThresholdBecomesABandlessCriteriaAndOldRowsStay() {
		flyway("48").migrate();
		long criteria = this.jdbc.sql("insert into " + SCHEMA + ".storyline_linking_criteria (window_seconds, threshold, "
				+ "created_at) values (1209600, 0.86, now()) returning id").query(Long.class).single();
		long storyline = this.jdbc.sql("insert into " + SCHEMA + ".storylines (model_version, centroid, article_count, "
				+ "first_article_at, last_article_at, representative_event_id, created_at, updated_at) "
				+ "values ('m', cast('[1,0]' as public.halfvec), 1, now(), now(), 1, now(), now()) returning id")
			.query(Long.class)
			.single();
		this.jdbc.sql("insert into " + SCHEMA + ".storyline_members (storyline_id, event_id, linked_by, score, cosine, "
				+ "key_term_overlap, criteria_id, linked_at) values (:storyline, 5, 'RULE', 0.9, 0.9, 0.5, :criteria, now())")
			.param("storyline", storyline)
			.param("criteria", criteria)
			.update();
		this.jdbc.sql("insert into " + SCHEMA + ".storyline_linking_queue (event_id, order_at, enqueued_at) "
				+ "values (6, now(), now())").update();

		flyway(null).migrate();

		assertThat(this.jdbc.sql("select window_seconds, lower, upper, probability from " + SCHEMA
				+ ".storyline_linking_criteria where id = :id").param("id", criteria).query().singleRow())
			.containsEntry("window_seconds", 1_209_600L)
			.containsEntry("lower", 0.86)
			.containsEntry("upper", 0.86)
			.containsEntry("probability", null);
		assertThat(this.jdbc.sql("select linked_by, criteria_id, probability, judgement_id from " + SCHEMA
				+ ".storyline_members where storyline_id = :id").param("id", storyline).query().singleRow())
			.containsEntry("linked_by", "RULE")
			.containsEntry("criteria_id", criteria)
			.containsEntry("probability", null)
			.containsEntry("judgement_id", null);
		assertThat(this.jdbc.sql("select retry_at from " + SCHEMA + ".storyline_linking_queue where event_id = 6")
			.query()
			.singleRow()).containsEntry("retry_at", null);
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
