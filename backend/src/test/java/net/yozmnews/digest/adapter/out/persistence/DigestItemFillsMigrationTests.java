package net.yozmnews.digest.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 빠진 항목 기록 칸을 더하는 마이그레이션(V74) — 실제 PostgreSQL 의 별도 스키마에 V73 까지만 올려 끝난 다이제스트 행을 넣고, V74 까지 올린 뒤
 * 그 행의 두 칸이 NULL(기록 전)로 남는지, 두 칸이 {@code READY} 에만 함께 있게 제약이 막는지 본다. 앱이 쓰는 스키마와 섞이지 않게 스키마를
 * 따로 두고 시험 뒤 지운다.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DigestItemFillsMigrationTests {

	private static final String SCHEMA = "v74_digest_item_fills";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void aReadyRowFinishedBeforeKeepsBothFillsEmpty() {
		flyway("73").migrate();
		long ready = digest("READY");

		flyway("74").migrate();

		Map<String, @Nullable Object> row = this.jdbc
			.sql("select first_missing_items, substituted_items from " + SCHEMA + ".digests where id = :id")
			.param("id", ready)
			.query()
			.singleRow();
		assertThat(row).containsEntry("first_missing_items", null).containsEntry("substituted_items", null);
	}

	@Test
	void theFillsAreOnlyOnAReadyRowAndAlwaysTogether() {
		flyway("74").migrate();
		long ready = digest("READY");
		long failed = digest("FAILED");

		fill(ready, 2, 1);
		assertThatThrownBy(() -> fill(failed, 0, 0)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> fill(ready, null, 0)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> fill(ready, 1, null)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> fill(ready, -1, 0)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private long digest(String status) {
		String failure = "FAILED".equals(status) ? "'NO_CITATION'" : "null";
		return this.jdbc.sql("insert into " + SCHEMA + ".digests (user_id, digest_date, period_start, period_end, status, "
				+ "trigger, error_code, created_at, dispatch_token, dispatched_at, started_at, completed_at, version) values (42, "
				+ "date '2026-11-16', timestamptz '2026-11-15T00:00:00Z', timestamptz '2026-11-16T00:00:00Z', '" + status
				+ "', 'MANUAL', " + failure + ", now(), gen_random_uuid(), now(), now(), now(), 0) returning id")
			.query(Long.class)
			.single();
	}

	private void fill(long digest, @Nullable Integer firstMissing, @Nullable Integer substituted) {
		this.jdbc.sql("update " + SCHEMA + ".digests set first_missing_items = :first, substituted_items = :substituted "
				+ "where id = :id")
			.param("first", firstMissing)
			.param("substituted", substituted)
			.param("id", digest)
			.update();
	}

	private Flyway flyway(String target) {
		return Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true).target(target).load();
	}

}
