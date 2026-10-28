package net.yozmnews.source.domain.registry;

import static net.yozmnews.source.domain.registry.RegistryFixtures.REVIEWED_TEXT;
import static net.yozmnews.source.domain.registry.RegistryFixtures.profile;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.domain.profile.PollInterval;
import net.yozmnews.source.domain.profile.PresetKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 소스 · 피드 aggregate 의 저장 — 피드 행은 SQL 로 만들고(version 0) aggregate 로 읽고 · 고치고 · 저장한다. 운영자 변경은 수집 상태
 * 행을 건드리지 않는다.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SourceFeedRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-10-28T09:00:00Z");

	@Autowired
	SourceRepository sources;

	@Autowired
	FeedRepository feeds;

	@Autowired
	JdbcClient jdbc;

	@Test
	void savesAndReadsBackASource() {
		Source saved = this.sources.save(Source.ofPreset(new PresetKey("alpha"), NOW));
		saved.changePollInterval(new PollInterval(Duration.ofMinutes(45)));
		this.sources.save(saved);

		Source read = this.sources.findById(saved.id().value()).orElseThrow();

		assertThat(read.presetKey()).contains(new PresetKey("alpha"));
		assertThat(read.operatorProfile()).isEmpty();
		assertThat(read.enabled()).isFalse();
		assertThat(read.pollInterval()).isEqualTo(new PollInterval(Duration.ofMinutes(45)));
	}

	@Test
	void anInsertedFeedRowCanBeEnabledThroughTheAggregate() {
		long source = this.sources.save(Source.ofOperatorProfile(REVIEWED_TEXT, NOW)).id().value();
		int inserted = this.feeds.insertIfAbsent(source, "one", NOW);
		int again = this.feeds.insertIfAbsent(source, "one", NOW.plusSeconds(60));
		Feed feed = this.feeds.findBySourceIdAndFeedKey(source, "one").orElseThrow();
		this.jdbc.sql("""
				insert into feed_poll_states (feed_id, next_poll_at, consecutive_failures, overflow_count)
				values (:feed, :at, 2, 0)
				""").param("feed", feed.id().value()).param("at", NOW.atOffset(java.time.ZoneOffset.UTC)).update();

		feed.enable(new ProfileResolution.Resolved(profile(REVIEWED_TEXT)));
		this.feeds.save(feed);

		assertThat(inserted).isEqualTo(1);
		assertThat(again).isZero();
		assertThat(this.feeds.findAllBySourceId(source)).hasSize(1);
		assertThat(this.jdbc.sql("select enabled, version from feeds where id = :id")
			.param("id", feed.id().value())
			.query((rs, row) -> rs.getBoolean("enabled") + "/" + rs.getLong("version"))
			.single()).isEqualTo("true/1");
		assertThat(this.jdbc.sql("select consecutive_failures from feed_poll_states where feed_id = :id")
			.param("id", feed.id().value())
			.query(Integer.class)
			.single()).isEqualTo(2);
	}

}
