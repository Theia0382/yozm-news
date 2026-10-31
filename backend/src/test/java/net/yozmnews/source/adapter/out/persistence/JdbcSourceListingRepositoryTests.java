package net.yozmnews.source.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.registry.SourceListingRepository.FeedRow;
import net.yozmnews.source.domain.registry.SourceListingRepository.SourceRow;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 운영자 목록 조회의 행 — 정렬 순서 · 정하지 않은 주기 · 운영자 원문 열을 Flyway 가 적용된 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: source-R1 · source-R14
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcSourceListingRepository.class })
class JdbcSourceListingRepositoryTests {

	@Autowired
	JdbcSourceListingRepository repository;

	@Autowired
	JdbcClient jdbc;

	/** 시험 트랜잭션 안에서 지운다 — 다른 시험 클래스가 남긴 행이 결과에 섞이지 않게(시험이 끝나면 되돌린다) */
	@BeforeEach
	void deleteRows() {
		this.jdbc.sql("delete from source_fetch_slots").update();
		this.jdbc.sql("delete from feed_poll_states").update();
		this.jdbc.sql("delete from feeds").update();
		this.jdbc.sql("delete from sources").update();
	}

	@Test
	void readsSourcesAndFeedsInIdOrderWithTheirSettings() {
		long operator = insertSource(null, "# 원문\nsource: {}\n", false, null);
		long preset = insertSource("multi", null, true, 30);
		long presetFeedTwo = insertFeed(preset, "two", true, 45);
		long operatorFeed = insertFeed(operator, "main", false, null);
		long presetFeedOne = insertFeed(preset, "one", false, null);

		assertThat(this.repository.findAllSources()).containsExactly(
				new SourceRow(new SourceId(operator), null, "# 원문\nsource: {}\n", false, null),
				new SourceRow(new SourceId(preset), "multi", null, true, 30));
		assertThat(this.repository.findAllFeeds()).containsExactly(
				new FeedRow(new FeedId(operatorFeed), new SourceId(operator), "main", false, null),
				new FeedRow(new FeedId(presetFeedTwo), new SourceId(preset), "two", true, 45),
				new FeedRow(new FeedId(presetFeedOne), new SourceId(preset), "one", false, null));
	}

	@Test
	void readsNothingFromAnEmptyRegistry() {
		assertThat(this.repository.findAllSources()).isEmpty();
		assertThat(this.repository.findAllFeeds()).isEmpty();
	}

	private long insertSource(@Nullable String presetKey, @Nullable String profile, boolean enabled,
			@Nullable Integer interval) {
		return this.jdbc.sql("""
				insert into sources (preset_key, profile, enabled, poll_interval_minutes, version, created_at)
				values (:preset, :profile, :enabled, :interval, 0, now()) returning id
				""")
			.param("preset", presetKey, Types.VARCHAR)
			.param("profile", profile, Types.VARCHAR)
			.param("enabled", enabled)
			.param("interval", interval, Types.INTEGER)
			.query(Long.class)
			.single();
	}

	private long insertFeed(long source, String key, boolean enabled, @Nullable Integer interval) {
		return this.jdbc.sql("""
				insert into feeds (source_id, feed_key, enabled, poll_interval_minutes, version, created_at)
				values (:source, :key, :enabled, :interval, 0, now()) returning id
				""")
			.param("source", source)
			.param("key", key)
			.param("enabled", enabled)
			.param("interval", interval, Types.INTEGER)
			.query(Long.class)
			.single();
	}

}
