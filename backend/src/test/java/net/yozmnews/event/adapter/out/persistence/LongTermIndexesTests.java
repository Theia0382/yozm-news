package net.yozmnews.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.EventArticles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 장기 검색 인덱스 — 닫힌 사건이 있는 모델 버전마다 사건 중심에 HNSW 식 · 부분 인덱스를 하나씩 만들고, 있으면 다시 만들지 않는다. 기사 표현에는
 * 인덱스가 없다. 동시 생성이라 트랜잭션 밖에서 돈다 — 시험이 만든 인덱스와 행은 끝에 지운다.
 * <p>
 * 지키는 규칙: event-R13
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcLongTermIndexes.class })
class LongTermIndexesTests {

	/** 작은따옴표가 든 모델 버전 — 리터럴 이스케이프를 본다 */
	private static final String QUOTED = "model-b's";

	@Autowired
	JdbcLongTermIndexes indexes;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	@BeforeEach
	@AfterEach
	void reset() {
		for (String name : indexNames()) {
			this.jdbc.sql("drop index if exists " + name).update();
		}
		EventArticles.clear(this.jdbc);
	}

	@Test
	void oneIndexPerModelVersionOfClosedEventsAndNoneTheSecondTime() {
		event("CLOSED", "model-a", "[1,0,0]");
		event("CLOSED", "model-a", "[0,1,0]");
		event("CLOSED", QUOTED, "[1,0]");
		// 열린 사건뿐인 모델 버전에는 만들지 않는다
		event("OPEN", "model-c", "[1,0,0,0]");

		int created = this.indexes.ensure();
		int again = this.indexes.ensure();

		assertThat(created).isEqualTo(2);
		assertThat(again).isZero();
		assertThat(indexNames()).containsExactlyInAnyOrder(JdbcLongTermIndexes.name("model-a"),
				JdbcLongTermIndexes.name(QUOTED));
		String definition = this.jdbc.sql("select indexdef from pg_indexes where indexname = :name")
			.param("name", JdbcLongTermIndexes.name("model-a"))
			.query(String.class)
			.single();
		assertThat(definition).contains("USING hnsw", "(centroid)::halfvec(3)", "halfvec_cosine_ops",
				"status = 'CLOSED'::text", "model_version = 'model-a'::text");
		assertThat(this.jdbc.sql("select indexdef from pg_indexes where indexname = :name")
			.param("name", JdbcLongTermIndexes.name(QUOTED))
			.query(String.class)
			.single()).contains("(centroid)::halfvec(2)", "model_version = 'model-b''s'::text");
	}

	@Test
	void theLongTermQueryUsesTheIndexOnlyAfterItIsMade() {
		event("CLOSED", "model-a", "[1,0,0]");
		event("CLOSED", "model-a", "[0,1,0]");
		// 통제 — 인덱스를 만들기 전의 같은 질의 계획에는 그 인덱스가 없다
		assertThat(plan()).doesNotContain(JdbcLongTermIndexes.name("model-a"));

		this.indexes.ensure();

		assertThat(plan()).contains(JdbcLongTermIndexes.name("model-a"));
	}

	@Test
	void representationsHaveNoIndexButTheirKey() {
		assertThat(this.jdbc.sql("select indexname from pg_indexes where tablename = 'article_match_features'")
			.query(String.class)
			.list()).containsExactly("article_match_features_pkey");
	}

	@Test
	void mixedDimensionsWithinAModelVersionMakeNoIndex() {
		event("CLOSED", "model-a", "[1,0,0]");
		event("CLOSED", "model-a", "[1,0]");

		assertThat(this.indexes.ensure()).isZero();
		assertThat(indexNames()).isEmpty();
	}

	/** 닫힌 사건 가운데 가까운 사건을 찾는 장기 검색 — 순차 탐색을 끈 같은 연결에서 계획을 읽는다 */
	private String plan() {
		List<String> lines = new TransactionTemplate(this.transactionManager).execute(status -> {
			this.jdbc.sql("set local enable_seqscan = off").update();
			return this.jdbc.sql("""
					explain select id from events
					where status = 'CLOSED' and model_version = 'model-a'
					order by centroid::halfvec(3) <=> cast('[1,0,0]' as halfvec(3))
					limit 1
					""").query(String.class).list();
		});
		return String.join("\n", Objects.requireNonNull(lines, "lines"));
	}

	private List<String> indexNames() {
		return this.jdbc.sql("select indexname from pg_indexes where indexname like :prefix")
			.param("prefix", JdbcLongTermIndexes.PREFIX + "%")
			.query(String.class)
			.list();
	}

	private void event(String status, String model, String centroid) {
		this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at, closed_at)
				values (:status, :model, cast(:centroid as halfvec), 1, now(), now(), now(), now(),
				        case when :status = 'OPEN' then null else now() end)
				""").param("status", status).param("model", model).param("centroid", centroid).update();
	}

}
