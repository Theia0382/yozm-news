package net.yozmnews.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
 * 사건 정리 마이그레이션(V18)의 채우기 — 실제 PostgreSQL 의 별도 스키마에 V17 까지만 올려 사건 · 배정을 넣고, 끝까지 올린 뒤 배정의 연 사건
 * 표지와 사건의 마지막으로 붙은 시각이 채워졌는지 본다. 앱이 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: event-R18(연 사건 표지) · event-R13(닫기의 기준 시각)
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EventMergeCloseMigrationTests {

	private static final String SCHEMA = "v18_backfill";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void fillsTheOpenedFlagAndTheLastJoinedTime() {
		flyway("17").migrate();
		long criteria = this.jdbc.sql("insert into " + SCHEMA + ".event_assignment_criteria "
				+ "(window_seconds, upper, lower, store, probability, created_at) "
				+ "values (259200, 0.8, 0.55, 0.4, 0.5, now()) returning id").query(Long.class).single();
		long assigned = event("2026-11-05T09:00:00Z");
		long empty = event("2026-11-05T08:00:00Z");
		// 기사 1 은 사건을 열었다(그 사건이 저장 후보에 없다), 기사 2 는 붙었다(그 사건이 저장 후보에 있다)
		assign(1, assigned, criteria, "2026-11-05T10:00:00Z");
		assign(2, assigned, criteria, "2026-11-05T11:00:00Z");
		candidate(1, empty);
		candidate(2, assigned);

		flyway(null).migrate();

		assertThat(opened(1)).isTrue();
		assertThat(opened(2)).isFalse();
		assertThat(lastJoined(assigned)).isEqualTo(Instant.parse("2026-11-05T11:00:00Z"));
		assertThat(lastJoined(empty)).isEqualTo(Instant.parse("2026-11-05T08:00:00Z"));
		assertThat(this.jdbc.sql("select revision from " + SCHEMA + ".events where id = :id")
			.param("id", assigned)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

	private long event(String createdAt) {
		return this.jdbc.sql("insert into " + SCHEMA + ".events (status, model_version, centroid, article_count, "
				+ "first_article_at, last_article_at, created_at) "
				+ "values ('OPEN', 'model-a', cast('[1,0]' as halfvec), 1, :t, :t, :t) returning id")
			.param("t", time(createdAt))
			.query(Long.class)
			.single();
	}

	private void assign(long article, long event, long criteria, String assignedAt) {
		this.jdbc.sql("insert into " + SCHEMA + ".event_articles (article_id, event_id, published_at, decided_by, "
				+ "criteria_id, assigned_at) values (:article, :event, :t, 'FORMULA', :criteria, :t)")
			.param("article", article)
			.param("event", event)
			.param("criteria", criteria)
			.param("t", time(assignedAt))
			.update();
	}

	private void candidate(long article, long event) {
		this.jdbc.sql("insert into " + SCHEMA + ".event_article_candidates (article_id, event_id, score, probability) "
				+ "values (:article, :event, 0.5, null)").param("article", article).param("event", event).update();
	}

	private boolean opened(long article) {
		return this.jdbc.sql("select opened from " + SCHEMA + ".event_articles where article_id = :article")
			.param("article", article)
			.query(Boolean.class)
			.single();
	}

	private Instant lastJoined(long event) {
		return this.jdbc.sql("select last_joined_at from " + SCHEMA + ".events where id = :id")
			.param("id", event)
			.query(OffsetDateTime.class)
			.single()
			.toInstant();
	}

	private static OffsetDateTime time(String instant) {
		return Instant.parse(instant).atOffset(ZoneOffset.UTC);
	}

}
