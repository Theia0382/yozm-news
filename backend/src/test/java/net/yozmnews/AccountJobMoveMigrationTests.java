package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

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
 * 반복 작업을 account 로 옮기는 마이그레이션(V83) — 실제 PostgreSQL 의 별도 스키마에 V82 까지만 올려 옛 클래스의 작업 행을 넣고 끝까지 올린 뒤,
 * 옮긴 세 반복 작업의 예약 · 대기 행만 지워지고 처리 중 · 끝난 행과 다른 작업의 행은 남는지 본다. 앱이 쓰는 스키마와 섞이지 않게 스키마를
 * 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: platform-R20
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AccountJobMoveMigrationTests {

	private static final String SCHEMA = "v83_account_job_moves";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void pendingJobsOfTheMovedRecurringJobsAreDropped() {
		flyway("82").migrate();
		job("00000000-0000-0000-0000-00000000000a", "expire-sessions", "SCHEDULED");
		job("00000000-0000-0000-0000-00000000000b", "forget-login-failures", "ENQUEUED");
		job("00000000-0000-0000-0000-00000000000c", "forget-request-counts", "AWAITING");
		job("00000000-0000-0000-0000-00000000000d", "expire-sessions", "PROCESSING");
		job("00000000-0000-0000-0000-00000000000e", "forget-login-failures", "SUCCEEDED");
		job("00000000-0000-0000-0000-00000000000f", "forget-read-notices", "SCHEDULED");

		flyway(null).migrate();

		assertThat(this.jdbc.sql("select recurringJobId || ':' || state from " + SCHEMA + ".jobrunr_jobs order by id")
			.query(String.class)
			.list()).containsExactly("expire-sessions:PROCESSING", "forget-login-failures:SUCCEEDED",
					"forget-read-notices:SCHEDULED");
	}

	private void job(String id, String recurringJob, String state) {
		this.jdbc.sql("insert into " + SCHEMA + ".jobrunr_jobs (id, version, jobAsJson, jobSignature, state, createdAt, "
				+ "updatedAt, scheduledAt, recurringJobId) values (:id, 1, '{}', 'signature', :state, now(), now(), "
				+ "now(), :recurring)")
			.param("id", id)
			.param("state", state)
			.param("recurring", recurringJob)
			.update();
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
