package net.yozmnews.tag.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.OffsetDateTime;
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
 * 시도 상한으로 실패로 남은 평가 대기를 되돌리는 마이그레이션(V67) — 실제 PostgreSQL 의 별도 스키마에 V66 까지만 올려 시도 소진 · 기사 거부 ·
 * 대기 중 · 점유 중인 행을 넣고, 끝까지 올린 뒤 시도 소진 행만 지금 평가할 수 있는 대기로 돌아왔는지(시도 수 · 마지막 문구는 그대로), 실패
 * 까닭에 시도 소진을 더는 쓸 수 없는지 본다. 앱이 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: tag-R20
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ExhaustedEvaluationRequeueMigrationTests {

	private static final String SCHEMA = "v67_exhausted_requeue";

	private static final OffsetDateTime ENQUEUED = OffsetDateTime.parse("2026-11-10T00:00:00Z");

	private static final OffsetDateTime ELIGIBLE = OffsetDateTime.parse("2026-11-10T00:30:00Z");

	private static final OffsetDateTime LEASE_EXPIRES = OffsetDateTime.parse("2026-11-10T01:00:00Z");

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void exhaustedRowsWaitAgainReadyNowAndOtherRowsStay() {
		flyway("66").migrate();
		insert(1, 5, null, "RETRIES_EXHAUSTED", "서버 오류");
		insert(2, 0, null, "ARTICLE_REJECTED", "입력 거부");
		insert(3, 2, null, null, "요청 제한");
		insert(4, 0, "8f0c3a52-6b1d-4e7a-9c2f-1a2b3c4d5e6f", null, null);
		OffsetDateTime before = this.jdbc.sql("select now()").query(OffsetDateTime.class).single();

		flyway(null).migrate();

		assertThat(row(1)).containsEntry("failure", null)
			.containsEntry("attempts", 5)
			.containsEntry("last_error", "서버 오류")
			.containsEntry("lease_token", null);
		assertThat(eligibleSince(1, before)).isTrue();
		assertThat(row(2)).containsEntry("failure", "ARTICLE_REJECTED")
			.containsEntry("attempts", 0)
			.containsEntry("last_error", "입력 거부");
		assertThat(row(3)).containsEntry("failure", null).containsEntry("attempts", 2).containsEntry("last_error", "요청 제한");
		assertThat(row(4)).containsEntry("failure", null)
			.containsEntry("lease_token", "8f0c3a52-6b1d-4e7a-9c2f-1a2b3c4d5e6f");
		for (long untouched : new long[] { 2, 3, 4 }) {
			assertThat(eligibleSince(untouched, ELIGIBLE) && !eligibleSince(untouched, ELIGIBLE.plusSeconds(1))).isTrue();
		}
		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> insert(5, 5, null, "RETRIES_EXHAUSTED", "서버 오류"));
	}

	private void insert(long article, int attempts, @Nullable String leaseToken, @Nullable String failure,
			@Nullable String lastError) {
		this.jdbc.sql("insert into " + SCHEMA + """
				.evaluation_queue (article_id, enqueued_at, eligible_at, attempts, lease_token, lease_expires_at,
				    failure, last_error)
				values (:article, :enqueued, :eligible, :attempts, cast(:token as uuid), :expires, :failure, :lastError)
				""")
			.param("article", article)
			.param("enqueued", ENQUEUED)
			.param("eligible", ELIGIBLE)
			.param("attempts", attempts)
			.param("token", leaseToken, java.sql.Types.VARCHAR)
			.param("expires", (leaseToken != null) ? LEASE_EXPIRES : null, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
			.param("failure", failure, java.sql.Types.VARCHAR)
			.param("lastError", lastError, java.sql.Types.VARCHAR)
			.update();
	}

	/** 평가 가능 시각이 {@code since} 이후(같으면 포함)인가 */
	private boolean eligibleSince(long article, OffsetDateTime since) {
		return this.jdbc.sql("select eligible_at >= :since from " + SCHEMA + ".evaluation_queue where article_id = :article")
			.param("since", since)
			.param("article", article)
			.query(Boolean.class)
			.single();
	}

	private Map<String, @Nullable Object> row(long article) {
		return this.jdbc.sql("select attempts, lease_token::text as lease_token, failure, last_error from "
				+ SCHEMA + ".evaluation_queue where article_id = :article")
			.param("article", article)
			.query()
			.singleRow();
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
