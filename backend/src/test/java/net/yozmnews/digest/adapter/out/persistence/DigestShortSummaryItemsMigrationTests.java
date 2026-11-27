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
 * 요약문이 짧은 항목 수 칸을 더하는 마이그레이션(V92) — 실제 PostgreSQL 의 별도 스키마에 V91 까지만 올려 시작한 다이제스트 행을 넣고, V92 까지
 * 올린 뒤 그 행의 칸이 NULL(기록 전)로 남는지, 값이 있으면 후보 수가 있고 그 수를 넘지 않게 제약이 막는지 본다. 앱이 쓰는 스키마와 섞이지 않게
 * 스키마를 따로 두고 시험 뒤 지운다.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DigestShortSummaryItemsMigrationTests {

	private static final String SCHEMA = "v92_digest_short_summary_items";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void aRowStartedBeforeKeepsTheCountEmpty() {
		flyway("91").migrate();
		long running = digest(3);

		flyway("92").migrate();

		Map<String, @Nullable Object> row = this.jdbc
			.sql("select candidate_count, short_summary_items from " + SCHEMA + ".digests where id = :id")
			.param("id", running)
			.query()
			.singleRow();
		assertThat(row).containsEntry("candidate_count", 3).containsEntry("short_summary_items", null);
	}

	@Test
	void theCountNeedsTheCandidateCountAndStaysWithinIt() {
		flyway("92").migrate();
		long started = digest(3);
		long notStarted = digest(null);

		count(started, 3);
		count(started, 0);
		assertThatThrownBy(() -> count(started, 4)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> count(started, -1)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> count(notStarted, 0)).isInstanceOf(DataIntegrityViolationException.class);
	}

	/** {@code RUNNING} 행 — 후보 수가 null 이면 시작 전 값이 없는 행 */
	private long digest(@Nullable Integer candidateCount) {
		return this.jdbc.sql("insert into " + SCHEMA + ".digests (user_id, digest_date, period_start, period_end, status, "
				+ "trigger, candidate_count, created_at, dispatch_token, dispatched_at, started_at, version) values (42, "
				+ "date '2026-11-16', timestamptz '2026-11-15T00:00:00Z', timestamptz '2026-11-16T00:00:00Z', 'RUNNING', "
				+ "'MANUAL', :count, now(), gen_random_uuid(), now(), now(), 0) returning id")
			.param("count", candidateCount)
			.query(Long.class)
			.single();
	}

	private void count(long digest, int shortSummaryItems) {
		this.jdbc.sql("update " + SCHEMA + ".digests set short_summary_items = :count where id = :id")
			.param("count", shortSummaryItems)
			.param("id", digest)
			.update();
	}

	private Flyway flyway(String target) {
		return Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true).target(target).load();
	}

}
