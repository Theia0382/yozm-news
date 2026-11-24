package net.yozmnews.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Time;
import java.time.LocalTime;
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
 * 자유 텍스트 관심사 칸을 지우는 마이그레이션(V84) — 실제 PostgreSQL 의 별도 스키마에 V83 까지만 올려 관심사가 든 설정 행을 넣고, V84 까지 올린
 * 뒤 칸이 없어졌는지와 그 행의 나머지 값이 그대로인지 본다. 앱이 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: user-R50 · docs/specs/user.md §6
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class InterestTextColumnMigrationTests {

	private static final String SCHEMA = "v84_interest_text";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void theColumnIsDroppedAndTheRowKeepsItsOtherValues() {
		flyway("83").migrate();
		this.jdbc.sql("insert into " + SCHEMA + ".user_settings (user_id, tag_set_id, timezone, digest_time, interest_text, "
				+ "weekly_report, weekly_report_day, monthly_report, candidate_volume, candidate_count, big_slots, "
				+ "summary_length, reasoning_effort, updated_at, version) values (7, 1, 'Asia/Seoul', '07:30', '반도체', "
				+ "false, 5, true, 'CUSTOM', 45, 0, 'SHORT', 'HIGH', now(), 4)").update();
		// 대조 — V83 에는 칸이 있다
		assertThat(columns()).contains("interest_text");

		flyway("84").migrate();

		assertThat(columns()).containsExactly("big_slots", "candidate_count", "candidate_volume", "digest_time",
				"llm_model", "llm_provider", "monthly_report", "reasoning_effort", "summary_length", "tag_set_id",
				"timezone", "updated_at", "user_id", "version", "weekly_report", "weekly_report_day");
		assertThat(this.jdbc.sql("select timezone, digest_time, weekly_report_day, candidate_count, summary_length, "
				+ "reasoning_effort, version from " + SCHEMA + ".user_settings where user_id = 7").query().singleRow())
			.containsEntry("timezone", "Asia/Seoul")
			.containsEntry("digest_time", Time.valueOf(LocalTime.of(7, 30)))
			.containsEntry("weekly_report_day", 5)
			.containsEntry("candidate_count", 45)
			.containsEntry("summary_length", "SHORT")
			.containsEntry("reasoning_effort", "HIGH")
			.containsEntry("version", 4L);
	}

	private List<String> columns() {
		return this.jdbc.sql("""
				select column_name from information_schema.columns
				where table_schema = :schema and table_name = 'user_settings'
				order by column_name
				""").param("schema", SCHEMA).query(String.class).list();
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
