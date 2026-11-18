package net.yozmnews.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * 요약 길이 · 추론 강도 칸을 더하는 마이그레이션(V55) — 실제 PostgreSQL 의 별도 스키마에 V54 까지만 올려 설정 행을 넣고, V55 까지 올린 뒤 그
 * 행이 기본값(요약 길이 보통 · 추론 강도 보통)으로 채워졌는지, 기본값을 지워 두 칸 없이 넣는 행은 거부되는지, 값 목록 밖의 값이 거부되는지 본다.
 * 칸을 더하기만 하는 마이그레이션이라 기존 칸의 값은 그대로다. 앱이 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DigestWritingSettingsMigrationTests {

	private static final String SCHEMA = "v53_digest_writing";

	/** 시험하는 마이그레이션의 버전 */
	private static final String THIS_VERSION = "55";

	private static final String SETTINGS_COLUMNS = "user_id, tag_set_id, timezone, digest_time, interest_text, "
			+ "weekly_report, weekly_report_day, monthly_report, candidate_volume, candidate_count, big_slots, "
			+ "updated_at, version";

	private static final String SETTINGS_VALUES = "'Asia/Seoul', '07:00', '배터리', true, 1, true, 'CUSTOM', 45, 0, "
			+ "now(), 0";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void existingSettingsRowsTakeTheDefaultsAndKeepTheirOtherValues() {
		flyway("54").migrate();
		long existing = user("old@example.com");
		this.jdbc.sql("insert into " + SCHEMA + ".user_settings (" + SETTINGS_COLUMNS + ") values (:user, 1, "
				+ SETTINGS_VALUES + ")").param("user", existing).update();

		flyway(THIS_VERSION).migrate();

		assertThat(this.jdbc.sql("select summary_length, reasoning_effort, interest_text, candidate_volume, "
				+ "candidate_count, big_slots from " + SCHEMA + ".user_settings where user_id = :user")
			.param("user", existing)
			.query()
			.singleRow()).containsEntry("summary_length", "NORMAL")
			.containsEntry("reasoning_effort", "MEDIUM")
			.containsEntry("interest_text", "배터리")
			.containsEntry("candidate_volume", "CUSTOM")
			.containsEntry("candidate_count", 45)
			.containsEntry("big_slots", 0);
	}

	@Test
	void newRowsMustCarryBothValuesFromTheirLists() {
		flyway(THIS_VERSION).migrate();
		long withoutValues = user("new@example.com");
		assertThatThrownBy(() -> this.jdbc.sql("insert into " + SCHEMA + ".user_settings (" + SETTINGS_COLUMNS
				+ ") values (:user, 1, " + SETTINGS_VALUES + ")").param("user", withoutValues).update())
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> settings(withoutValues, "'LONG', 'LOW'"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> settings(withoutValues, "'SHORT', 'NONE'"))
			.isInstanceOf(DataIntegrityViolationException.class);

		settings(withoutValues, "'SHORT', 'HIGH'");
		assertThat(this.jdbc.sql("select summary_length, reasoning_effort from " + SCHEMA
				+ ".user_settings where user_id = :user").param("user", withoutValues).query().singleRow())
			.containsEntry("summary_length", "SHORT")
			.containsEntry("reasoning_effort", "HIGH");
	}

	private void settings(long user, String values) {
		this.jdbc.sql("insert into " + SCHEMA + ".user_settings (" + SETTINGS_COLUMNS
				+ ", summary_length, reasoning_effort) values (:user, 1, " + SETTINGS_VALUES + ", " + values + ")")
			.param("user", user)
			.update();
	}

	private long user(String email) {
		return this.jdbc.sql("insert into " + SCHEMA + ".users (email, display_name, role, status, created_at, version) "
				+ "values (:email, 'u', 'USER', 'ACTIVE', now(), 0) returning id")
			.param("email", email)
			.query(Long.class)
			.single();
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
