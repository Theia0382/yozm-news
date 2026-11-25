package net.yozmnews.report.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import javax.sql.DataSource;

import net.yozmnews.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
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
 * 리포트 일정 칸을 더하는 마이그레이션(V87) — 실제 PostgreSQL 의 별도 스키마에 V86 까지만 올려 생성을 기다리는 행 · 끝난 행을 넣고, V87 까지
 * 올린 뒤 두 행이 새 등록 토큰 · 만든 시각의 등록 시각 · 예약 시각 · 다시 만든 횟수 0 으로 채워지고 다른 값은 그대로인지 본다. 앱이 쓰는
 * 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ReportScheduleMigrationTests {

	private static final String SCHEMA = "v87_report_schedule";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void existingRowsTakeANewTokenAndTheirCreatedTimeAndKeepTheirOtherValues() {
		flyway("86").migrate();
		this.jdbc.sql("insert into " + SCHEMA + ".reports (user_id, period_kind, period_start, period_end, status, "
				+ "created_at, version) values (7, 'WEEK', timestamptz '2026-11-09 00:00Z', timestamptz '2026-11-16 00:00Z', "
				+ "'SCHEDULED', timestamptz '2026-11-16 01:00Z', 0)").update();
		this.jdbc.sql("insert into " + SCHEMA + ".reports (user_id, period_kind, period_start, period_end, status, "
				+ "provider, model, input_tokens, output_tokens, stats_snapshot, zone, created_at, started_at, completed_at, "
				+ "version) values (7, 'WEEK', timestamptz '2026-11-16 00:00Z', timestamptz '2026-11-23 00:00Z', 'READY', "
				+ "'openai', 'gpt-5.5-mini', 120, 45, cast('{}' as jsonb), 'Asia/Seoul', timestamptz '2026-11-23 01:00Z', "
				+ "timestamptz '2026-11-23 01:00Z', timestamptz '2026-11-23 01:05Z', 3)").update();

		flyway("87").migrate();

		assertThat(this.jdbc.sql("select status, dispatch_token is not null as token, dispatched_at = created_at as dispatched, "
				+ "scheduled_at = created_at as scheduled, regenerations, coalesce(input_tokens, -1) as input_tokens, version from "
				+ SCHEMA
				+ ".reports order by id").query().listOfRows()).containsExactly(
						Map.of("status", "SCHEDULED", "token", true, "dispatched", true, "scheduled", true,
								"regenerations", 0, "input_tokens", -1, "version", 0L),
						Map.of("status", "READY", "token", true, "dispatched", true, "scheduled", true, "regenerations",
								0, "input_tokens", 120, "version", 3L));
	}

	private Flyway flyway(String target) {
		return Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true).target(target).load();
	}

}
