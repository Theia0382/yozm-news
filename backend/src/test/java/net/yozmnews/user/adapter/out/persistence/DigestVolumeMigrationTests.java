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
 * 다이제스트 분량 칸을 더하는 마이그레이션(V53) — 실제 PostgreSQL 의 별도 스키마에 V50 까지만 올려 설정 행을 넣고, 끝까지 올린 뒤 그 행이
 * 기본값(보통 · 직접 입력 없음 · 큰 슬롯 3)으로 채워졌는지, 기본값을 지워 세 칸 없이 넣는 행은 거부되는지 본다. 앱이 쓰는 스키마와 섞이지 않게
 * 스키마를 따로 두고 테스트 뒤 지운다.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DigestVolumeMigrationTests {

	private static final String SCHEMA = "v50_digest_volume";

	private static final String SETTINGS_COLUMNS = "user_id, tag_set_id, timezone, digest_time, interest_text, "
			+ "weekly_report, weekly_report_day, monthly_report, updated_at, version";

	private static final String SETTINGS_VALUES = "'Asia/Seoul', '07:00', '', true, 1, true, now(), 0";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void existingSettingsRowsTakeTheDefaultVolumeAndNewRowsMustCarryIt() {
		flyway("50").migrate();
		long existing = user("old@example.com");
		this.jdbc.sql("insert into " + SCHEMA + ".user_settings (" + SETTINGS_COLUMNS + ") values (:user, 1, "
				+ SETTINGS_VALUES + ")").param("user", existing).update();

		flyway(null).migrate();

		assertThat(this.jdbc.sql("select candidate_volume, candidate_count, big_slots from " + SCHEMA
				+ ".user_settings where user_id = :user").param("user", existing).query().singleRow())
			.containsEntry("candidate_volume", "NORMAL")
			.containsEntry("candidate_count", null)
			.containsEntry("big_slots", 3);

		long withoutVolume = user("new@example.com");
		assertThatThrownBy(() -> this.jdbc.sql("insert into " + SCHEMA + ".user_settings (" + SETTINGS_COLUMNS
				+ ") values (:user, 1, " + SETTINGS_VALUES + ")").param("user", withoutVolume).update())
			.isInstanceOf(DataIntegrityViolationException.class);

		this.jdbc.sql("insert into " + SCHEMA + ".user_settings (" + SETTINGS_COLUMNS
				+ ", candidate_volume, candidate_count, big_slots) values (:user, 1, " + SETTINGS_VALUES
				+ ", 'CUSTOM', 45, 0)").param("user", withoutVolume).update();
		assertThat(this.jdbc.sql("select candidate_volume, candidate_count, big_slots from " + SCHEMA
				+ ".user_settings where user_id = :user").param("user", withoutVolume).query().singleRow())
			.containsEntry("candidate_volume", "CUSTOM")
			.containsEntry("candidate_count", 45)
			.containsEntry("big_slots", 0);
	}

	@Test
	void theCustomCountComesOnlyWithTheCustomVolume() {
		flyway(null).migrate();
		long presetWithCount = user("preset@example.com");
		assertThatThrownBy(() -> settings(presetWithCount, "'NORMAL', 45, 3"))
			.isInstanceOf(DataIntegrityViolationException.class);
		long customWithoutCount = user("custom@example.com");
		assertThatThrownBy(() -> settings(customWithoutCount, "'CUSTOM', null, 3"))
			.isInstanceOf(DataIntegrityViolationException.class);

		settings(presetWithCount, "'NORMAL', null, 3");
		settings(customWithoutCount, "'CUSTOM', 45, 3");
		assertThat(this.jdbc.sql("select count(*) from " + SCHEMA + ".user_settings").query(Long.class).single())
			.isEqualTo(2L);
	}

	private void settings(long user, String volume) {
		this.jdbc.sql("insert into " + SCHEMA + ".user_settings (" + SETTINGS_COLUMNS
				+ ", candidate_volume, candidate_count, big_slots) values (:user, 1, " + SETTINGS_VALUES + ", " + volume
				+ ")").param("user", user).update();
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
