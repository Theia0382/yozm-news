package net.yozmnews.source.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import net.yozmnews.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소스 · 피드 스키마의 제약 — 실제 PostgreSQL 에서 Flyway 마이그레이션을 적용해 본다. 제약 위반은 그 트랜잭션을 중단시키므로
 * 테스트를 트랜잭션으로 감싸지 않고 행을 직접 지운다.
 * <p>
 * 지키는 규칙: source-R1 · source-R2 · source-R3
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SourceFeedSchemaTests {

	private static final String TEXT_WITH_COMMENT = """
			# 이유: 이 언론사는 dc:date 를 쓴다
			source:
			  name: 합성일보
			""";

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void deleteRows() {
		this.jdbc.sql("delete from feed_poll_states").update();
		this.jdbc.sql("delete from feeds").update();
		this.jdbc.sql("delete from sources").update();
	}

	@Test
	void aSourceHasExactlyOneOfAPresetKeyAndAProfile() {
		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> insertSource("'both'", "'text'"));
		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> insertSource("null", "null"));
	}

	@Test
	void twoSourcesCannotReferenceOnePreset() {
		insertSource("'alpha'", "null");

		assertThatExceptionOfType(DuplicateKeyException.class).isThrownBy(() -> insertSource("'alpha'", "null"));
	}

	@Test
	void aSourceHasOneRowPerFeedKey() {
		long source = insertSource("'alpha'", "null");
		insertFeed(source, "all");

		assertThatExceptionOfType(DuplicateKeyException.class).isThrownBy(() -> insertFeed(source, "all"));
		insertFeed(insertSource("'beta'", "null"), "all");
	}

	@Test
	void aPollStateKeepsTheLeaseAndTheLastErrorWhole() {
		long feed = this.jdbc.sql("""
				insert into feeds (source_id, feed_key, enabled, version, created_at) values (:source, 'all', false, 0, now())
				returning id
				""").param("source", insertSource("'alpha'", "null")).query(Long.class).single();

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> insertPollState(feed, "gen_random_uuid()", "null", "null", "null"));
		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> insertPollState(feed, "null", "null", "'TIMEOUT'", "null"));
		insertPollState(feed, "gen_random_uuid()", "now()", "'TIMEOUT'", "now()");
	}

	@Test
	void keepsTheOperatorProfileTextVerbatim() {
		long source = this.jdbc.sql("""
				insert into sources (profile, enabled, version, created_at) values (:text, false, 0, now()) returning id
				""").param("text", TEXT_WITH_COMMENT).query(Long.class).single();

		assertThat(this.jdbc.sql("select profile from sources where id = :id").param("id", source).query(String.class)
			.single()).isEqualTo(TEXT_WITH_COMMENT).contains("# 이유: 이 언론사는 dc:date 를 쓴다");
	}

	private long insertSource(String presetKey, String profile) {
		return this.jdbc.sql("insert into sources (preset_key, profile, enabled, version, created_at) values (" + presetKey
				+ ", " + profile + ", false, 0, now()) returning id").query(Long.class).single();
	}

	private void insertPollState(long feed, String leaseToken, String leaseScheduledAt, String errorReason,
			String errorAt) {
		this.jdbc.sql("insert into feed_poll_states (feed_id, next_poll_at, lease_token, lease_scheduled_at,"
				+ " consecutive_failures, last_error_reason, last_error_message, last_error_at, overflow_count) values (:feed,"
				+ " now(), " + leaseToken + ", " + leaseScheduledAt + ", 0, " + errorReason + ", '문구', " + errorAt + ", 0)")
			.param("feed", feed)
			.update();
	}

	private void insertFeed(long source, String key) {
		this.jdbc.sql("""
				insert into feeds (source_id, feed_key, enabled, version, created_at) values (:source, :key, false, 0, now())
				""").param("source", source).param("key", key).update();
	}

}
