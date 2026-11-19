package net.yozmnews.digest.adapter.out.persistence;

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
 * 섹션을 항목 단위로 바꾸는 마이그레이션(V70) — 실제 PostgreSQL 의 별도 스키마에 V69 까지만 올려 기사 단위의 옛 섹션 · 인용 · 후보 행을 넣고,
 * V70 까지 올린 뒤 그 행이 모두 지워지고 다이제스트 행은 남는지, 섹션 종류 검사가 새 값만 받는지, 섹션 사건 행이 섹션을 따라 지워지는지 본다. 앱이
 * 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 시험 뒤 지운다.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DigestSectionsByItemMigrationTests {

	private static final String SCHEMA = "v66_digest_sections";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void oldSectionsAndCandidatesAreRemovedAndTheDigestRowStays() {
		flyway("69").migrate();
		long digest = digest();
		long overview = section(digest, "OVERVIEW", 0);
		long headlines = section(digest, "MORE_HEADLINES", 1);
		this.jdbc.sql("insert into " + SCHEMA + ".digest_items (digest_section_id, position, article_id, note, "
				+ "selection_reason) values (:section, 0, 11, '한 줄', 'SUBSCRIBED')").param("section", headlines).update();
		this.jdbc.sql("insert into " + SCHEMA + ".digest_candidates (digest_id, alias, article_id) values (:digest, 'A1', 11)")
			.param("digest", digest)
			.update();
		assertThat(overview).isPositive();

		flyway("70").migrate();

		assertThat(count("digest_sections")).isZero();
		assertThat(count("digest_items")).isZero();
		assertThat(count("digest_candidates")).isZero();
		assertThat(this.jdbc.sql("select status from " + SCHEMA + ".digests where id = :id")
			.param("id", digest)
			.query(String.class)
			.single()).isEqualTo("READY");
	}

	@Test
	void theKindTakesOnlyTheNewValuesAndSectionEventsFollowTheirSection() {
		flyway("70").migrate();
		long digest = digest();

		assertThatThrownBy(() -> section(digest, "MORE_HEADLINES", 1)).isInstanceOf(DataIntegrityViolationException.class);
		long headline = section(digest, "HEADLINE", 1);
		section(digest, "TOP_STORY", 2);
		this.jdbc.sql("insert into " + SCHEMA + ".digest_section_events (digest_section_id, position, event_id, "
				+ "selection_reason) values (:section, 0, 30, 'MAJOR_STORYLINE'), (:section, 1, 40, 'INTEREST')")
			.param("section", headline)
			.update();
		assertThatThrownBy(() -> this.jdbc.sql("insert into " + SCHEMA + ".digest_section_events (digest_section_id, "
				+ "position, event_id, selection_reason) values (:section, 2, 50, 'OTHER')").param("section", headline).update())
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(count("digest_section_events")).isEqualTo(2);

		this.jdbc.sql("delete from " + SCHEMA + ".digest_sections where id = :id").param("id", headline).update();

		assertThat(count("digest_section_events")).isZero();
	}

	private long digest() {
		return this.jdbc.sql("insert into " + SCHEMA + ".digests (user_id, digest_date, period_start, period_end, status, "
				+ "trigger, created_at, dispatch_token, dispatched_at, started_at, completed_at, version) values (42, "
				+ "date '2026-11-16', timestamptz '2026-11-15T00:00:00Z', timestamptz '2026-11-16T00:00:00Z', 'READY', "
				+ "'MANUAL', now(), gen_random_uuid(), now(), now(), now(), 0) returning id")
			.query(Long.class)
			.single();
	}

	private long section(long digest, String kind, int position) {
		return this.jdbc.sql("insert into " + SCHEMA + ".digest_sections (digest_id, kind, position) "
				+ "values (:digest, :kind, :position) returning id")
			.param("digest", digest)
			.param("kind", kind)
			.param("position", position)
			.query(Long.class)
			.single();
	}

	private long count(String table) {
		return this.jdbc.sql("select count(*) from " + SCHEMA + "." + table).query(Long.class).single();
	}

	private Flyway flyway(String target) {
		return Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true).target(target).load();
	}

}
