package net.yozmnews.storyline.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 큰 판정 기준의 꼴을 바꾸는 마이그레이션(V40) — 실제 PostgreSQL 의 별도 스키마에 V39 까지만 올려 옛 기준과 이력을 넣고, 끝까지 올린 뒤 두
 * 테이블이 비어 있고 새 열만 있는지 본다. 옛 값과 이력은 옮기지 않는다. 앱이 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: storyline-R20
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class BigCriteriaIntensityMigrationTests {

	private static final String SCHEMA = "v39_big_criteria";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void theOldCriteriaAndHistoryAreDroppedAndTheNewColumnsStartEmpty() {
		flyway("39").migrate();
		this.jdbc.sql("insert into " + SCHEMA + ".storyline_big_criteria (id, min_sources, min_importance, changed_at) "
				+ "values (1, 4, 3.50, now())").update();
		this.jdbc.sql("insert into " + SCHEMA + ".storyline_big_criteria_changes "
				+ "(previous_min_sources, previous_min_importance, min_sources, min_importance, actor, changed_at) "
				+ "values (3, 3.00, 4, 3.50, 'OPERATOR', now())").update();

		flyway(null).migrate();

		assertThat(count("storyline_big_criteria")).isZero();
		assertThat(count("storyline_big_criteria_changes")).isZero();
		assertThat(columns("storyline_big_criteria")).containsExactly("changed_at", "id", "min_intensity");
		assertThat(columns("storyline_big_criteria_changes")).containsExactly("actor", "changed_at", "id", "min_intensity",
				"previous_min_intensity");
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

	private long count(String table) {
		return this.jdbc.sql("select count(*) from " + SCHEMA + "." + table).query(Long.class).single();
	}

	private List<String> columns(String table) {
		return this.jdbc.sql("select column_name from information_schema.columns "
				+ "where table_schema = :schema and table_name = :table order by column_name")
			.param("schema", SCHEMA)
			.param("table", table)
			.query(String.class)
			.list();
	}

}
