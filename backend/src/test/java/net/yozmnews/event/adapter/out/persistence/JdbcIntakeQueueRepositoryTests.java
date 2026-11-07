package net.yozmnews.event.adapter.out.persistence;

import static net.yozmnews.Latches.awaitQuietly;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.intake.IntakeLease;
import net.yozmnews.event.domain.intake.PendingArticle;
import org.jspecify.annotations.Nullable;
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
 * 사건 묶기 대기 저장소 — 넣기 · 배정 없는 기사만 넣기 · 잠금 대상과 순서 · 토큰 조건 반영 · 지우기 · 동시 잠금을 Flyway 가 적용된 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: event-R5 · event-R6 · event-R8
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcIntakeQueueRepository.class })
class JdbcIntakeQueueRepositoryTests {

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	private static final UUID TOKEN = UUID.fromString("00000000-0000-4000-8000-000000000001");

	private static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-000000000002");

	@Autowired
	JdbcIntakeQueueRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void enqueuesOnceAndKeepsTheFirstRow() {
		this.repository.enqueue(new ArticleId(1), T);
		this.repository.enqueue(new ArticleId(1), T.plusSeconds(300));

		assertThat(this.jdbc.sql("select article_id, enqueued_at, lease_token from event_intake_queue")
			.query()
			.listOfRows()).hasSize(1).first().satisfies(row -> {
				assertThat(row).containsEntry("article_id", 1L).containsEntry("lease_token", null);
				assertThat(instant(row.get("enqueued_at"))).isEqualTo(Instant.parse("2026-11-05T00:00:00Z"));
			});
	}

	@Test
	void enqueuesOnlyUnassignedArticlesAndCountsTheNewRows() {
		long event = this.jdbc.sql("""
				insert into events (status, model_version, centroid, article_count, first_article_at, last_article_at,
				                    created_at, last_joined_at)
				values ('OPEN', 'model-a', '[1,0]', 1, now(), now(), now(), now()) returning id
				""").query(Long.class).single();
		long criteria = this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, 0.8, 0.55, 0.4, 0.5, now()) returning id
				""").query(Long.class).single();
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, decided_by, opened, criteria_id, assigned_at)
				values (52, :event, now(), 'FORMULA', false, :criteria, now())
				""").param("event", event).param("criteria", criteria).update();
		row(53, T, null, null);

		int enqueued = this.repository.enqueueUnassigned(
				List.of(new ArticleId(51), new ArticleId(52), new ArticleId(53), new ArticleId(54)), T.plusSeconds(60));

		assertThat(enqueued).isEqualTo(2);
		assertThat(this.jdbc.sql("select article_id from event_intake_queue order by article_id").query(Long.class).list())
			.containsExactly(51L, 53L, 54L);
		assertThat(this.repository.lockDue(T.plusSeconds(60), 10)).extracting(PendingArticle::enqueuedAt)
			.containsExactly(T, T.plusSeconds(60), T.plusSeconds(60));
		assertThat(this.repository.enqueueUnassigned(List.of(), T)).isZero();
	}

	@Test
	void locksFreeAndExpiredRowsInEnqueueOrderUpToTheLimit() {
		Instant now = Instant.parse("2026-11-05T01:00:00Z");
		row(11, T.plusSeconds(20), null, null);                          // 점유 없음
		row(12, T.plusSeconds(10), OTHER, now.minusSeconds(1));          // 점유 만료
		row(13, T.plusSeconds(10), OTHER, now);                          // 만료 시각 = 지금
		row(14, T, OTHER, now.plusSeconds(1));                           // 점유 중 — 오지 않는다
		row(15, T.plusSeconds(30), null, null);                          // 상한 밖

		List<PendingArticle> due = this.repository.lockDue(now, 3);

		assertThat(due).extracting(pending -> pending.articleId().value()).containsExactly(12L, 13L, 11L);
		assertThat(due.get(2)).isEqualTo(new PendingArticle(new ArticleId(11), Instant.parse("2026-11-05T00:00:20Z"), null));
		assertThat(due.get(0).lease()).isEqualTo(new IntakeLease(OTHER, Instant.parse("2026-11-05T00:59:59Z")));
	}

	@Test
	void savesTheLeaseOfALockedRow() {
		row(21, T, null, null);
		PendingArticle locked = this.repository.lockDue(T, 10).get(0);

		this.repository.saveLocked(locked.claim(TOKEN, T));

		assertThat(lease(21)).containsEntry("lease_token", TOKEN);
		assertThat(instant(lease(21).get("lease_expires_at"))).isEqualTo(Instant.parse("2026-11-05T00:10:00Z"));
	}

	@Test
	void deletesOnlyWithTheMatchingToken() {
		row(31, T, TOKEN, T.plusSeconds(600));

		assertThat(this.repository.deleteIfLeased(new ArticleId(31), OTHER)).isFalse();
		assertThat(count(31)).isEqualTo(1);
		assertThat(this.repository.deleteIfLeased(new ArticleId(31), TOKEN)).isTrue();
		assertThat(count(31)).isZero();
	}

	@Test
	void releasesOnlyWithTheMatchingToken() {
		row(41, T, TOKEN, T.plusSeconds(600));

		assertThat(this.repository.releaseIfLeased(new ArticleId(41), OTHER)).isFalse();
		assertThat(lease(41)).containsEntry("lease_token", TOKEN);
		assertThat(this.repository.releaseIfLeased(new ArticleId(41), TOKEN)).isTrue();
		assertThat(lease(41)).containsEntry("lease_token", null).containsEntry("lease_expires_at", null);
	}

	@Test
	void deletesTheGivenArticlesOnly() {
		row(51, T, null, null);
		row(52, T, TOKEN, T.plusSeconds(600));
		row(53, T, null, null);

		this.repository.delete(List.of(new ArticleId(51), new ArticleId(52)));
		this.repository.delete(List.of());

		assertThat(this.jdbc.sql("select article_id from event_intake_queue").query(Long.class).list()).containsExactly(53L);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void aSecondRoundSkipsRowsLockedByTheFirst() throws InterruptedException {
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		row(60, T, null, null);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			CompletableFuture<List<PendingArticle>> holder = CompletableFuture
				.supplyAsync(() -> transaction.execute(status -> {
					List<PendingArticle> due = this.repository.lockDue(T, 10);
					locked.countDown();
					awaitQuietly(release);
					return due;
				}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			// 앞 회차가 잡은 행은 기다리지 않고 건너뛴다 — 기다린다면 200ms 뒤 55P03 으로 실패한다
			List<PendingArticle> whileLocked = transaction.execute(status -> {
				this.jdbc.sql("set local lock_timeout = '200ms'").update();
				return this.repository.lockDue(T, 10);
			});
			release.countDown();
			List<PendingArticle> firstRound = holder.join();
			List<PendingArticle> afterRelease = transaction.execute(status -> this.repository.lockDue(T, 10));

			assertThat(firstRound).extracting(pending -> pending.articleId().value()).containsExactly(60L);
			assertThat(whileLocked).isEmpty();
			assertThat(afterRelease).extracting(pending -> pending.articleId().value()).containsExactly(60L);
		}
		finally {
			release.countDown();
			this.jdbc.sql("delete from event_intake_queue where article_id = 60").update();
		}
	}

	private void row(long article, Instant enqueuedAt, @Nullable UUID token, @Nullable Instant expiresAt) {
		this.jdbc.sql("""
				insert into event_intake_queue (article_id, enqueued_at, lease_token, lease_expires_at)
				values (:article, :enqueued, :token, :expires)
				""")
			.param("article", article)
			.param("enqueued", enqueuedAt.atOffset(ZoneOffset.UTC))
			.param("token", token, Types.OTHER)
			.param("expires", (expiresAt != null) ? expiresAt.atOffset(ZoneOffset.UTC) : null,
					Types.TIMESTAMP_WITH_TIMEZONE)
			.update();
	}

	private Map<String, @Nullable Object> lease(long article) {
		return this.jdbc.sql("select lease_token, lease_expires_at from event_intake_queue where article_id = :article")
			.param("article", article)
			.query()
			.singleRow();
	}

	private long count(long article) {
		return this.jdbc.sql("select count(*) from event_intake_queue where article_id = :article")
			.param("article", article)
			.query(Long.class)
			.single();
	}

	private static Instant instant(@Nullable Object timestamp) {
		return switch (timestamp) {
			case OffsetDateTime offset -> offset.toInstant();
			case java.sql.Timestamp sql -> sql.toInstant();
			case null, default -> throw new IllegalStateException("시각이 아니다: " + timestamp);
		};
	}

}
