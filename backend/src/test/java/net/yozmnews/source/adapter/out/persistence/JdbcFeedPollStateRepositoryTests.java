package net.yozmnews.source.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.domain.fetch.FetchFailureReason;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.poll.FeedPollState;
import net.yozmnews.source.domain.poll.ItemKey;
import net.yozmnews.source.domain.poll.LastError;
import net.yozmnews.source.domain.poll.Lease;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 수집 상태의 왕복 — 모든 열이 값 그대로 돌아온다. 직전 항목 키는 null(첫 수집 전) · 빈 배열 · 여러 키가 서로 다르게 남는다.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcFeedPollStateRepository.class })
class JdbcFeedPollStateRepositoryTests {

	private static final Instant AT = Instant.parse("2026-10-28T10:00:12.345678Z");

	@Autowired
	JdbcClient jdbc;

	@Autowired
	JdbcFeedPollStateRepository repository;

	private long source;

	@BeforeEach
	void createSource() {
		this.source = this.jdbc.sql("""
				insert into sources (preset_key, enabled, version, created_at) values ('alpha', false, 0, now()) returning id
				""").query(Long.class).single();
	}

	@Test
	void roundTripsAnInitialState() {
		FeedPollState initial = FeedPollState.initial(feed("a"), AT);

		this.repository.insertIfAbsent(initial);

		assertThat(this.repository.find(initial.feedId())).contains(initial);
		assertThat(this.repository.find(initial.feedId()).orElseThrow().previousItemKeys()).isNull();
	}

	@Test
	void roundTripsEveryColumn() {
		FeedPollState state = new FeedPollState(feed("b"), Instant.parse("2026-10-28T10:10:10Z"),
				new Lease(UUID.fromString("0a1b2c3d-0000-4000-8000-000000000001"), Instant.parse("2026-10-28T10:00:10Z")),
				new FetchState("W/\"abc\"", "Wed, 28 Oct 2026 10:00:00 GMT"), AT, Instant.parse("2026-10-27T08:00:00Z"),
				Instant.parse("2026-10-26T07:00:00Z"), 3,
				new LastError(FetchFailureReason.SERVER_ERROR, "서버 오류 응답 (HTTP 503)", 503, AT),
				URI.create("https://news.example/rss/new"), Instant.parse("2026-10-25T06:00:00Z"), 4,
				List.of(new ItemKey("g:urn:1"), new ItemKey("l:https://news.example/2"), new ItemKey("g:urn:3")));

		this.repository.insertIfAbsent(state);

		assertThat(this.repository.find(state.feedId())).contains(state);
	}

	@Test
	void keepsAnEmptyItemKeyListApartFromNone() {
		FeedPollState empty = withKeys(feed("c"), List.of());
		FeedPollState none = withKeys(feed("d"), null);

		this.repository.insertIfAbsent(empty);
		this.repository.insertIfAbsent(none);

		assertThat(this.repository.find(empty.feedId()).orElseThrow().previousItemKeys()).isEmpty();
		assertThat(this.repository.find(none.feedId()).orElseThrow().previousItemKeys()).isNull();
	}

	@Test
	void insertingTwiceKeepsTheFirstRow() {
		FeedId feed = feed("e");
		FeedPollState first = FeedPollState.initial(feed, AT);

		this.repository.insertIfAbsent(first);
		this.repository.insertIfAbsent(FeedPollState.initial(feed, Instant.parse("2026-10-29T00:00:00Z")));

		assertThat(this.repository.find(feed)).contains(first);
	}

	@Test
	void findsNothingForAFeedWithoutState() {
		assertThat(this.repository.find(feed("f"))).isEmpty();
	}

	private FeedId feed(String key) {
		return new FeedId(this.jdbc.sql("""
				insert into feeds (source_id, feed_key, enabled, version, created_at)
				values (:source, :key, false, 0, now()) returning id
				""").param("source", this.source).param("key", key).query(Long.class).single());
	}

	private static FeedPollState withKeys(FeedId feed, @Nullable List<ItemKey> keys) {
		return new FeedPollState(feed, AT, null, FetchState.NONE, null, null, null, 0, null, null, null, 0, keys);
	}

}
