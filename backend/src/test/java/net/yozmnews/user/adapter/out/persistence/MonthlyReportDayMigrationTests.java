package net.yozmnews.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import net.yozmnews.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
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
 * 월간 리포트 날짜 칸을 더하는 마이그레이션(V86) — 실제 PostgreSQL 의 별도 스키마에 V85 까지만 올려 설정 행을 넣고, V86 까지 올린 뒤 그 행이
 * 기본값(1일)으로 채워지고 다른 값은 그대로인지, 기본값을 지워 칸 없이 넣는 행은 거부되는지, 1 ~ 31 밖의 값이 거부되는지 본다. 앱이 쓰는 스키마와
 * 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MonthlyReportDayMigrationTests {

	private static final String SCHEMA = "v86_monthly_report_day";

	private static final String COLUMNS = "user_id, tag_set_id, timezone, digest_time, weekly_report, weekly_report_day, "
			+ "monthly_report, candidate_volume, candidate_count, big_slots, summary_length, reasoning_effort, updated_at, "
			+ "version";

	private static final String VALUES = "1, 'Asia/Seoul', '07:30', false, 5, true, 'CUSTOM', 45, 0, 'SHORT', 'HIGH', "
			+ "now(), 4";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void existingRowsTakeTheFirstDayAndKeepTheirOtherValues() {
		flyway("85").migrate();
		this.jdbc.sql("insert into " + SCHEMA + ".user_settings (" + COLUMNS + ") values (7, " + VALUES + ")").update();

		flyway("86").migrate();

		assertThat(this.jdbc.sql("select monthly_report_day, monthly_report, weekly_report_day, candidate_count, version "
				+ "from " + SCHEMA + ".user_settings where user_id = 7").query().singleRow())
			.containsEntry("monthly_report_day", 1)
			.containsEntry("monthly_report", true)
			.containsEntry("weekly_report_day", 5)
			.containsEntry("candidate_count", 45)
			.containsEntry("version", 4L);
	}

	@Test
	void newRowsMustCarryADayFromOneToThirtyOne() {
		flyway("86").migrate();
		assertThatThrownBy(() -> this.jdbc
			.sql("insert into " + SCHEMA + ".user_settings (" + COLUMNS + ") values (8, " + VALUES + ")")
			.update()).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> settings(9, 0)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> settings(10, 32)).isInstanceOf(DataIntegrityViolationException.class);

		// 대조 — 경계 값은 받는다
		settings(11, 1);
		settings(12, 31);
		assertThat(this.jdbc.sql("select monthly_report_day from " + SCHEMA + ".user_settings order by user_id")
			.query(Integer.class)
			.list()).containsExactly(1, 31);
	}

	private void settings(long user, int day) {
		this.jdbc.sql("insert into " + SCHEMA + ".user_settings (" + COLUMNS + ", monthly_report_day) values (:user, "
				+ VALUES + ", :day)").param("user", user).param("day", day).update();
	}

	private Flyway flyway(String target) {
		return Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true).target(target).load();
	}

}
