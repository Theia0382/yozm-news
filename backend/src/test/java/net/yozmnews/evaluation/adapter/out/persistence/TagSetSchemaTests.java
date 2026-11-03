package net.yozmnews.evaluation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.application.TagSetRows;
import org.junit.jupiter.api.AfterEach;
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
 * 태그 체계 스키마 제약 — 실제 PostgreSQL 에서 고유 · 활성 하나 · 부모(커밋 때 확인) · 종류별 열 · 기준 범위 · 계보의 새 키(커밋 때
 * 확인) · 계보 종류를 본다.
 * <p>
 * 부모 FK 는 커밋 때 확인하므로 시험 트랜잭션으로 감싸지 않고 문장마다 커밋한다. 넣은 행은 끝에 지운다.
 * <p>
 * 지키는 규칙: evaluation-R3 · evaluation-R6 · evaluation-R10 · evaluation-R22
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TagSetSchemaTests {

	private static final String UNIQUE_VIOLATION = "23505";

	private static final String FOREIGN_KEY_VIOLATION = "23503";

	private static final String CHECK_VIOLATION = "23514";

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	@AfterEach
	void removeRows() {
		TagSetRows.removeAll(this.jdbc);
	}

	@Test
	void keysAreUniqueWithinAVersionAndKindButNotAcrossKinds() {
		long version = insertTagSet(1, true);
		insertTag(version, "TOPIC", "economy", null, false);
		insertTag(version, "REGION", "economy", null, false);

		assertThat(sqlState(() -> insertTag(version, "TOPIC", "economy", null, false))).isEqualTo(UNIQUE_VIOLATION);
	}

	@Test
	void onlyOneVersionIsActive() {
		insertTagSet(1, true);
		insertTagSet(2, false);

		assertThat(sqlState(() -> insertTagSet(3, true))).isEqualTo(UNIQUE_VIOLATION);
	}

	@Test
	void aParentMustBeATopicOfTheSameVersionCheckedAtCommit() {
		long version = insertTagSet(1, true);
		insertTag(version, "TOPIC", "economy", null, false);
		insertTag(version, "TOPIC", "economy.markets", "economy", false);
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		transaction.executeWithoutResult(status -> {
			// 부모보다 먼저 넣은 자식 — 같은 트랜잭션 안에서 부모가 들어오면 커밋 때 성립한다
			insertTag(version, "TOPIC", "world.politics", "world", false);
			insertTag(version, "TOPIC", "world", null, false);
		});

		assertThat(sqlState(() -> transaction.executeWithoutResult(
				status -> insertTag(version, "TOPIC", "culture.arts", "culture", false))))
			.isEqualTo(FOREIGN_KEY_VIOLATION);
	}

	@Test
	void aLineageTargetMustBeATagOfTheSameVersionAndKindCheckedAtCommit() {
		long version = insertTagSet(1, true);
		insertTag(version, "REGION", "overseas", null, false);
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		transaction.executeWithoutResult(status -> {
			// 새 키보다 먼저 넣은 계보 행 — 같은 트랜잭션 안에서 태그 행이 들어오면 커밋 때 성립한다
			insertLineage(version, "REGION", "japan", "nippon", "RENAME");
			insertTag(version, "REGION", "nippon", null, false);
		});
		insertLineage(version, "REGION", "abroad", "overseas", "RENAME");

		assertThat(sqlState(() -> transaction.executeWithoutResult(
				status -> insertLineage(version, "REGION", "korea", "joseon", "RENAME"))))
			.isEqualTo(FOREIGN_KEY_VIOLATION);
		// 같은 키라도 다른 태그 종류의 태그 행은 새 키가 아니다
		assertThat(sqlState(() -> insertLineage(version, "FORMAT", "abroad", "overseas", "RENAME")))
			.isEqualTo(FOREIGN_KEY_VIOLATION);
		// 이전 키는 기준 버전의 키라 FK 가 없다
		assertThat(this.jdbc.sql("select count(*) from tag_key_lineage").query(Long.class).single()).isEqualTo(2);
	}

	@Test
	void lineageRowsHaveAClosedKindAndTagKind() {
		long version = insertTagSet(1, true);
		insertTag(version, "REGION", "overseas", null, false);

		assertThat(sqlState(() -> insertLineage(version, "REGION", "abroad", "overseas", "MOVE")))
			.isEqualTo(CHECK_VIOLATION);
		assertThat(sqlState(() -> insertLineage(version, "PLACE", "abroad", "overseas", "RENAME")))
			.isEqualTo(CHECK_VIOLATION);
	}

	@Test
	void onlyTopicsHaveParentsAndOnlyFormatsAreExcludedByDefault() {
		long version = insertTagSet(1, true);
		insertTag(version, "REGION", "domestic", null, false);
		insertTag(version, "FORMAT", "brief", null, true);

		assertThat(sqlState(() -> insertTag(version, "REGION", "domestic.seoul", "domestic", false)))
			.isEqualTo(CHECK_VIOLATION);
		assertThat(sqlState(() -> insertTag(version, "TOPIC", "economy", null, true))).isEqualTo(CHECK_VIOLATION);
	}

	@Test
	void thresholdsStayWithinZeroAndOneForTopicsAndRegions() {
		long version = insertTagSet(1, true);
		insertThreshold(version, "TOPIC", "1", "0");

		assertThat(sqlState(() -> insertThreshold(version, "REGION", "1.5", "0.1"))).isEqualTo(CHECK_VIOLATION);
		assertThat(sqlState(() -> insertThreshold(version, "FORMAT", "0.5", "0.1"))).isEqualTo(CHECK_VIOLATION);
	}

	private long insertTagSet(int number, boolean active) {
		return this.jdbc.sql("""
				insert into tag_sets (number, active, topic_template, region_template, format_question,
				    importance_question, created_by, created_at)
				values (:number, :active, '{description}', '{description}', '형식', '중요도', 'SYSTEM', now())
				returning id
				""")
			.param("number", number)
			.param("active", active)
			.query(Long.class)
			.single();
	}

	private void insertTag(long version, String kind, String key, String parent, boolean excluded) {
		this.jdbc.sql("""
				insert into tags (tag_set_id, kind, key, parent_key, name, description, excluded_by_default, sort_order)
				values (:version, :kind, :key, :parent, '이름', '설명', :excluded, 0)
				""")
			.param("version", version)
			.param("kind", kind)
			.param("key", key)
			.param("parent", parent)
			.param("excluded", excluded)
			.update();
	}

	private void insertLineage(long version, String kind, String from, String to, String change) {
		this.jdbc.sql("""
				insert into tag_key_lineage (tag_set_id, tag_kind, from_key, to_key, kind)
				values (:version, :kind, :from, :to, :change)
				""")
			.param("version", version)
			.param("kind", kind)
			.param("from", from)
			.param("to", to)
			.param("change", change)
			.update();
	}

	private void insertThreshold(long version, String kind, String tag, String store) {
		this.jdbc.sql("""
				insert into tag_set_thresholds (tag_set_id, tag_kind, tag_threshold, store_threshold)
				values (:version, :kind, cast(:tag as numeric), cast(:store as numeric))
				""")
			.param("version", version)
			.param("kind", kind)
			.param("tag", tag)
			.param("store", store)
			.update();
	}

	private static String sqlState(Runnable statement) {
		Throwable thrown = catchThrowable(statement::run);
		assertThat(thrown).as("제약 위반이 나야 한다").isNotNull();
		for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sql.getSQLState() != null) {
				return sql.getSQLState();
			}
		}
		throw new AssertionError("SQL 상태가 없는 예외: " + thrown, thrown);
	}

}
