package net.yozmnews.article.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * 엔트리의 소스 채우기 마이그레이션(V6) — 실제 PostgreSQL 의 별도 스키마에 V5 까지만 올려 행을 넣고, 끝까지 올린 뒤 엔트리마다 그 피드의
 * 소스가 채워졌는지 본다. 앱이 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: article-R7(엔트리의 소스)
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ArticleFeedEntrySourceMigrationTests {

	private static final String SCHEMA = "v6_backfill";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void fillsEachEntryWithTheSourceOfItsFeed() {
		flyway("5").migrate();
		long first = source("a");
		long second = source("b");
		// 피드를 소스와 엇갈린 순서로 만들어 피드 ID 와 그 소스 ID 가 늘 다르다 — 피드 ID 를 옮겨 적은 채우기는 통과하지 않는다
		long secondFeed = feed(second);
		long firstFeed = feed(first);
		long article = article("https://a.example/news/1", first);
		entry(article, firstFeed, "NEW");
		entry(article, secondFeed, "URL");

		flyway(null).migrate();

		assertThat(this.jdbc.sql("select feed_id, source_id from " + SCHEMA + ".article_feed_entries order by feed_id")
			.query()
			.listOfRows()).containsExactly(Map.of("feed_id", secondFeed, "source_id", second),
					Map.of("feed_id", firstFeed, "source_id", first));
		assertThat(this.jdbc.sql("""
				select column_name from information_schema.columns where table_schema = :schema and table_name = 'articles'
				""").param("schema", SCHEMA).query(String.class).list()).isNotEmpty().doesNotContain("source_id");
		assertThat(this.jdbc.sql("""
				select is_nullable from information_schema.columns
				where table_schema = :schema and table_name = 'article_feed_entries' and column_name = 'source_id'
				""").param("schema", SCHEMA).query(String.class).single()).isEqualTo("NO");
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

	private long source(String presetKey) {
		return this.jdbc.sql("insert into " + SCHEMA + ".sources (preset_key, enabled, version, created_at) "
				+ "values (:key, false, 0, now()) returning id").param("key", presetKey).query(Long.class).single();
	}

	private long feed(long sourceId) {
		return this.jdbc.sql("insert into " + SCHEMA + ".feeds (source_id, feed_key, enabled, version, created_at) "
				+ "values (:source, 'all', false, 0, now()) returning id").param("source", sourceId)
			.query(Long.class)
			.single();
	}

	private long article(String url, long sourceId) {
		return this.jdbc.sql("insert into " + SCHEMA + ".articles (source_id, canonical_url, url_hash, title, "
				+ "title_key_hash, published_at, published_at_estimated, first_seen_at) "
				+ "values (:source, :url, :urlHash, '제목', :titleHash, now(), false, now()) returning id")
			.param("source", sourceId)
			.param("url", url)
			.param("urlHash", sha256(url))
			.param("titleHash", sha256("제목"))
			.query(Long.class)
			.single();
	}

	private void entry(long articleId, long feedId, String matchKind) {
		this.jdbc.sql("insert into " + SCHEMA + ".article_feed_entries (article_id, feed_id, item_url_hash, "
				+ "feed_categories, published_at_estimated, match_kind, seen_at) "
				+ "values (:article, :feed, :itemUrlHash, '{}', false, :kind, now())")
			.param("article", articleId)
			.param("feed", feedId)
			.param("itemUrlHash", sha256("item-" + feedId))
			.param("kind", matchKind)
			.update();
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
