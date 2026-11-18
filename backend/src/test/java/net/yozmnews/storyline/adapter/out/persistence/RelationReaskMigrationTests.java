package net.yozmnews.storyline.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.util.List;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관계를 다시 묻게 하는 마이그레이션(V56) — 실제 PostgreSQL 의 별도 스키마에 V55 까지만 올려 스토리라인 · 관계 · 물은 기록 · 관계 대기
 * 행을 넣고, 끝까지 올린 뒤 물은 기록이 비고 합쳐지지 않은 스토리라인이 모두 관계 대기에 있고 지금 관계는 그대로인지 본다. 앱이 쓰는
 * 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: storyline-R30 · storyline-R31
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RelationReaskMigrationTests {

	private static final String SCHEMA = "v55_relation_reask";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void everyLiveStorylineIsQueuedAgainWithNoAskedRecordsAndRelationsStay() {
		flyway("55").migrate();
		long waiting = storyline(null);
		long idle = storyline(null);
		// waiting 에 합쳐진 스토리라인 — 대기에 들지 않아야 한다
		storyline(waiting);
		long criteria = this.jdbc.sql("insert into " + SCHEMA + ".storyline_relation_criteria (probability, one_matter, "
				+ "min_events, candidate_score, window_seconds, created_at) values (0.6, 0.2, 3, 0.88, 1209600, now()) "
				+ "returning id").query(Long.class).single();
		this.jdbc.sql("insert into " + SCHEMA + ".storyline_relations (kind, source_id, target_id, probability, "
				+ "judgement_id, criteria_id, linked_at) values ('CONTAINS', :source, :target, 0.7, 41, :criteria, now())")
			.param("source", waiting)
			.param("target", idle)
			.param("criteria", criteria)
			.update();
		this.jdbc.sql("insert into " + SCHEMA + ".storyline_relation_asked (low_id, high_id, smaller_events, outcome, "
				+ "judgement_id, criteria_id, asked_at) values (:low, :high, 3, 'LINKED', 41, :criteria, now())")
			.param("low", waiting)
			.param("high", idle)
			.param("criteria", criteria)
			.update();
		// 이미 대기에 있는 스토리라인 — 알림 수 2
		this.jdbc.sql("insert into " + SCHEMA + ".storyline_relation_queue (storyline_id, notifications, enqueued_at) "
				+ "values (:id, 2, now())").param("id", waiting).update();

		flyway(null).migrate();

		assertThat(this.jdbc.sql("select count(*) from " + SCHEMA + ".storyline_relation_asked")
			.query(Long.class)
			.single()).isZero();
		// 합쳐지지 않은 스토리라인마다 한 행 — 있던 행은 알림 수가 하나 늘고, 합쳐진 스토리라인은 없다
		assertThat(this.jdbc.sql("select storyline_id, notifications, failures from " + SCHEMA
				+ ".storyline_relation_queue order by storyline_id").query().listOfRows()).containsExactly(
						Map.of("storyline_id", waiting, "notifications", 3, "failures", 0),
						Map.of("storyline_id", idle, "notifications", 0, "failures", 0));
		// 지금 관계는 남는다 — 다시 물은 쌍의 판정이 고쳐 쓴다
		assertThat(this.jdbc.sql("select kind, source_id, target_id, judgement_id from " + SCHEMA + ".storyline_relations")
			.query()
			.listOfRows())
			.isEqualTo(List.of(Map.of("kind", "CONTAINS", "source_id", waiting, "target_id", idle, "judgement_id", 41L)));
	}

	/** 스토리라인 하나 — mergedInto 가 있으면 그 스토리라인에 합쳐진 것 */
	private long storyline(@Nullable Long mergedInto) {
		return this.jdbc.sql("insert into " + SCHEMA + ".storylines (model_version, centroid, article_count, "
				+ "first_article_at, last_article_at, representative_event_id, created_at, updated_at, merged_into, merged_at) "
				+ "values ('m', cast('[1,0]' as public.halfvec), 1, now(), now(), 1, now(), now(), :into, "
				+ "case when cast(:into as bigint) is null then null else now() end) returning id")
			.param("into", mergedInto, Types.BIGINT)
			.query(Long.class)
			.single();
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
