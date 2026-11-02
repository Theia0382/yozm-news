package net.yozmnews.evaluation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
 * 평가 대기의 이행 투입(V10) — 실제 PostgreSQL 의 별도 스키마에 V9 까지만 올려 기사를 넣고, 끝까지 올린 뒤 그 기사들이 대기에 들어갔는지
 * 본다. 앱이 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: evaluation-R18
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EvaluationQueueMigrationTests {

	private static final String SCHEMA = "v10_queue";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void queuesEveryArticleStoredBeforeTheModuleExistedReadyNow() {
		flyway("9").migrate();
		List<Long> articles = List.of(article("https://a.example/1"), article("https://a.example/2"),
				article("https://a.example/3"));

		flyway(null).migrate();

		assertThat(this.jdbc.sql("select article_id from " + SCHEMA + ".evaluation_queue order by article_id")
			.query(Long.class)
			.list()).isEqualTo(articles);
		assertThat(this.jdbc.sql("""
				select count(*) from %s.evaluation_queue
				where attempts = 0 and failure is null and last_error is null and lease_token is null
				    and lease_expires_at is null and eligible_at = enqueued_at
				""".formatted(SCHEMA)).query(Long.class).single()).isEqualTo(3);
	}

	@Test
	void noArticlesMeansAnEmptyQueue() {
		flyway(null).migrate();

		assertThat(this.jdbc.sql("select count(*) from " + SCHEMA + ".evaluation_queue").query(Long.class).single())
			.isZero();
		assertThat(this.jdbc.sql("""
				select table_name from information_schema.tables where table_schema = :schema
				    and table_name in ('evaluation_queue', 'article_evaluations', 'article_tags')
				""").param("schema", SCHEMA).query(String.class).list())
			.containsExactlyInAnyOrder("evaluation_queue", "article_evaluations", "article_tags");
	}

	private long article(String url) {
		return this.jdbc.sql("""
				insert into %s.articles (canonical_url, url_hash, title, title_key_hash, published_at,
				    published_at_estimated, first_seen_at)
				values (:url, sha256(convert_to(:url, 'UTF8')), '제목', sha256(convert_to('제목', 'UTF8')),
				    '2026-10-20T00:00:00Z', false, '2026-10-20T00:00:00Z')
				returning id
				""".formatted(SCHEMA)).param("url", url).query(Long.class).single();
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
