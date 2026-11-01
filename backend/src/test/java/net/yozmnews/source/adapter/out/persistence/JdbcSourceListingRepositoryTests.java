package net.yozmnews.source.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedStatus;
import net.yozmnews.source.FetchFailureReason;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.poll.FeedPollState;
import net.yozmnews.source.domain.poll.LastError;
import net.yozmnews.source.domain.registry.SourceListingRepository.FeedRow;
import net.yozmnews.source.domain.registry.SourceListingRepository.PollRow;
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
 * 운영자 목록 조회의 행 — 정렬 순서 · 정하지 않은 주기 · 운영자 원문 열 · 피드의 수집 상태를 Flyway 가 적용된 실제 PostgreSQL 에서
 * 본다.
 * <p>
 * 지키는 규칙: source-R1 · source-R14 · source-R51
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcSourceListingRepository.class, JdbcFeedPollStateRepository.class })
class JdbcSourceListingRepositoryTests {

	private static final Instant AT = Instant.parse("2026-10-28T10:00:12Z");

	@Autowired
	JdbcSourceListingRepository repository;

	@Autowired
	JdbcFeedPollStateRepository pollStates;

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
				new FeedRow(new FeedId(operatorFeed), new SourceId(operator), "main", false, null, null),
				new FeedRow(new FeedId(presetFeedTwo), new SourceId(preset), "two", true, 45, null),
				new FeedRow(new FeedId(presetFeedOne), new SourceId(preset), "one", false, null, null));
	}

	@Test
	void readsEachFeedsCollectionStateAndKeepsAFeedWithoutOne() {
		long source = insertSource("states", null, true, null);
		FeedId failing = new FeedId(insertFeed(source, "failing", true, null));
		FeedId fresh = new FeedId(insertFeed(source, "fresh", true, null));
		FeedId orphan = new FeedId(insertFeed(source, "orphan", false, null));
		FeedPollState failingState = new FeedPollState(failing, Instant.parse("2026-10-31T05:00:00Z"), null,
				FetchState.NONE, Instant.parse("2026-10-31T04:00:00Z"), Instant.parse("2026-10-30T22:00:00Z"),
				Instant.parse("2026-10-30T21:00:00Z"), Instant.parse("2026-10-30T20:30:00Z"), 6, 2,
				new LastError(FetchFailureReason.ACCESS_DENIED, "접근 거부", 403, Instant.parse("2026-10-31T04:00:00Z")),
				null, null, 0, List.of());
		this.pollStates.insertIfAbsent(failingState);
		this.pollStates.insertIfAbsent(FeedPollState.initial(fresh, AT));
		this.jdbc.sql("update feed_poll_states set status = 'DEGRADED', status_changed_at = :at where feed_id = :feed")
			.param("at", Instant.parse("2026-10-31T04:30:00Z").atOffset(ZoneOffset.UTC))
			.param("feed", failing.value())
			.update();

		assertThat(this.repository.findAllFeeds()).containsExactly(
				new FeedRow(failing, new SourceId(source), "failing", true, null,
						new PollRow(failingState, FeedStatus.DEGRADED, Instant.parse("2026-10-31T04:30:00Z"))),
				new FeedRow(fresh, new SourceId(source), "fresh", true, null,
						new PollRow(FeedPollState.initial(fresh, AT), FeedStatus.ACTIVE, null)),
				new FeedRow(orphan, new SourceId(source), "orphan", false, null, null));
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
