package net.yozmnews.source.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.poll.FeedPollState;
import net.yozmnews.source.domain.poll.FeedStatus;
import net.yozmnews.source.domain.poll.FeedStatusTarget;
import net.yozmnews.source.domain.poll.Lease;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 피드 상태 저장소의 대상 잠금과 상태 쓰기 — Flyway 가 적용된 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: source-R47 · source-R48
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcFeedPollStateRepository.class, JdbcFeedStatusRepository.class })
class JdbcFeedStatusRepositoryTests {

	private static final Instant AT = Instant.parse("2026-10-28T10:00:12Z");

	@Autowired
	JdbcClient jdbc;

	@Autowired
	JdbcFeedPollStateRepository pollStates;

	@Autowired
	JdbcFeedStatusRepository repository;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void locksOnlyFeedsWhoseSourceAndFeedAreBothEnabledInFeedOrder() {
		long enabledSource = source("on", true);
		long disabledSource = source("off", false);
		FeedId first = feed(enabledSource, "first", true);
		FeedId second = feed(enabledSource, "second", true);
		FeedId disabledFeed = feed(enabledSource, "disabled", false);
		FeedId ofDisabledSource = feed(disabledSource, "any", true);
		for (FeedId feed : List.of(first, second, disabledFeed, ofDisabledSource)) {
			this.pollStates.insertIfAbsent(FeedPollState.initial(feed, AT));
		}
		this.jdbc.sql("update feed_poll_states set status = 'STALE' where feed_id = :feed")
			.param("feed", second.value())
			.update();

		List<FeedStatusTarget> targets = this.repository.lockTargets()
			.stream()
			.filter(target -> List.of(first, second, disabledFeed, ofDisabledSource).contains(target.state().feedId()))
			.toList();

		assertThat(targets).containsExactly(new FeedStatusTarget(FeedPollState.initial(first, AT), FeedStatus.ACTIVE),
				new FeedStatusTarget(FeedPollState.initial(second, AT), FeedStatus.STALE));
	}

	@Test
	void savesOnlyTheStatusAndTheTimeItChanged() {
		FeedId feed = feed(source("on", true), "only", true);
		FeedPollState state = new FeedPollState(feed, AT, null, FetchState.NONE, AT, AT, AT,
				Instant.parse("2026-10-27T03:00:00Z"), 3, 1, null, null, null, 0, List.of());
		this.pollStates.insertIfAbsent(state);

		this.repository.saveStatus(feed, FeedStatus.DEGRADED, Instant.parse("2026-10-30T00:00:00Z"));

		assertThat(this.jdbc.sql("select status, status_changed_at from feed_poll_states where feed_id = :feed")
			.param("feed", feed.value())
			.query((rs, row) -> rs.getString(1) + " " + rs.getObject(2, OffsetDateTime.class).toInstant())
			.single()).isEqualTo("DEGRADED 2026-10-30T00:00:00Z");
		assertThat(this.pollStates.find(feed)).contains(state);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void collectionWaitsWhileTheHealthCheckHoldsTheRows() throws InterruptedException {
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		long source = source("locking", true);
		FeedId feed = feed(source, "held", true);
		UUID token = UUID.fromString("0a1b2c3d-0000-4000-8000-000000000003");
		this.pollStates.insertIfAbsent(FeedPollState.initial(feed, AT).claim(new Lease(token, AT), AT));
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			CompletableFuture<List<FeedStatusTarget>> holder = CompletableFuture
				.supplyAsync(() -> transaction.execute(status -> {
					List<FeedStatusTarget> targets = this.repository.lockTargets();
					locked.countDown();
					awaitQuietly(release);
					return targets;
				}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			// 건강 검사가 잡은 행은 수집 결과 반영이 기다린다 — 500ms 안에 풀리지 않으면 55P03 으로 실패한다
			assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> transaction.execute(status -> {
					this.jdbc.sql("set local lock_timeout = '500ms'").update();
					return this.pollStates.replaceIfLeased(token, FeedPollState.initial(feed, AT));
				}))
				.satisfies(ex -> assertThat(((SQLException) ex.getMostSpecificCause()).getSQLState()).isEqualTo("55P03"));
			release.countDown();
			List<FeedStatusTarget> held = holder.join();
			Boolean afterRelease = transaction.execute(status -> {
				this.jdbc.sql("set local lock_timeout = '500ms'").update();
				return this.pollStates.replaceIfLeased(token, FeedPollState.initial(feed, AT));
			});

			assertThat(held).extracting(target -> target.state().feedId()).contains(feed);
			assertThat(afterRelease).isTrue();
		}
		finally {
			release.countDown();
			this.jdbc.sql("delete from feed_poll_states where feed_id = :feed").param("feed", feed.value()).update();
			this.jdbc.sql("delete from feeds where id = :feed").param("feed", feed.value()).update();
			this.jdbc.sql("delete from sources where id = :source").param("source", source).update();
		}
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private long source(String key, boolean enabled) {
		return this.jdbc.sql("""
				insert into sources (preset_key, enabled, version, created_at) values (:key, :enabled, 0, now()) returning id
				""").param("key", "status-" + key + "-" + UUID.randomUUID()).param("enabled", enabled)
			.query(Long.class)
			.single();
	}

	private FeedId feed(long source, String key, boolean enabled) {
		return new FeedId(this.jdbc.sql("""
				insert into feeds (source_id, feed_key, enabled, version, created_at)
				values (:source, :key, :enabled, 0, now()) returning id
				""").param("source", source).param("key", key).param("enabled", enabled).query(Long.class).single());
	}

}
