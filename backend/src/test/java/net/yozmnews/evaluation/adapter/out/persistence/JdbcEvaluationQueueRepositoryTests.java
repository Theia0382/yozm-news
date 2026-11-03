package net.yozmnews.evaluation.adapter.out.persistence;

import static net.yozmnews.Latches.awaitQuietly;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EvaluationQueueCounts;
import net.yozmnews.evaluation.domain.queue.EvaluationLease;
import net.yozmnews.evaluation.domain.queue.PendingEvaluation;
import net.yozmnews.evaluation.domain.queue.QueueFailure;
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
 * 평가 대기 저장소 — 넣기 · 재평가 넣기 · 잠금 대상 · 토큰 조건 반영 · 건수 · 동시 잠금을 Flyway 가 적용된 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: evaluation-R18 · evaluation-R19 · evaluation-R20 · evaluation-R25
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcEvaluationQueueRepository.class })
class JdbcEvaluationQueueRepositoryTests {

	private static final Instant T = Instant.parse("2026-11-02T00:00:00Z");

	private static final UUID TOKEN = UUID.fromString("00000000-0000-4000-8000-000000000001");

	private static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-000000000002");

	@Autowired
	JdbcEvaluationQueueRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void enqueuesOnceWithTheSettleDelayAndKeepsTheFirstRow() {
		this.repository.enqueue(new ArticleId(1), T);
		this.repository.enqueue(new ArticleId(1), T.plusSeconds(300));

		assertThat(this.jdbc.sql("select article_id, enqueued_at, eligible_at, attempts from evaluation_queue")
			.query()
			.listOfRows()).hasSize(1).first().satisfies(row -> {
				assertThat(row).containsEntry("article_id", 1L).containsEntry("attempts", 0);
				assertThat(instant(row.get("enqueued_at"))).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
				assertThat(instant(row.get("eligible_at"))).isEqualTo(Instant.parse("2026-11-02T00:30:00Z"));
			});
	}

	@Test
	void requeuesNewRowsAndFailedRowsAtOnceAndLeavesWaitingAndLeasedRowsAlone() {
		Instant now = Instant.parse("2026-11-09T00:00:00Z");
		row(11, T.plusSeconds(1800), 0, null, null, null);                    // 대기 중 — 정착 시간 뒤
		row(12, T.plusSeconds(60), 2, OTHER, now.plusSeconds(600), null);     // 회차가 점유 중
		row(13, T, 0, null, null, "ARTICLE_REJECTED");                        // 기사 거부로 실패
		row(14, T, 5, null, null, "RETRIES_EXHAUSTED");                       // 시도 소진으로 실패
		this.jdbc.sql("update evaluation_queue set last_error = '거부' where article_id in (13, 14)").update();

		int requeued = this.repository.requeue(
				List.of(new ArticleId(10), new ArticleId(11), new ArticleId(12), new ArticleId(13), new ArticleId(14)),
				now);

		assertThat(requeued).isEqualTo(3);
		assertThat(rowOf(10)).containsEntry("attempts", 0).containsEntry("failure", null).containsEntry("lease_token", null);
		assertThat(instant(rowOf(10).get("eligible_at"))).isEqualTo(Instant.parse("2026-11-09T00:00:00Z"));
		assertThat(instant(rowOf(10).get("enqueued_at"))).isEqualTo(Instant.parse("2026-11-09T00:00:00Z"));
		for (long failed : List.of(13L, 14L)) {
			assertThat(rowOf(failed)).containsEntry("attempts", 0).containsEntry("failure", null)
				.containsEntry("last_error", null);
			assertThat(instant(rowOf(failed).get("eligible_at"))).isEqualTo(Instant.parse("2026-11-09T00:00:00Z"));
		}
		assertThat(instant(rowOf(11).get("eligible_at"))).isEqualTo(Instant.parse("2026-11-02T00:30:00Z"));
		assertThat(rowOf(12)).containsEntry("attempts", 2).containsEntry("lease_token", OTHER);
		assertThat(instant(rowOf(12).get("eligible_at"))).isEqualTo(Instant.parse("2026-11-02T00:01:00Z"));
		assertThat(this.repository.requeue(List.of(), now)).isZero();
		// 같은 기사가 두 번 와도 한 행이다
		assertThat(this.repository.requeue(List.of(new ArticleId(15), new ArticleId(15)), now)).isEqualTo(1);
	}

	@Test
	void locksOnlyRowsThatAreDueUnfailedAndNotValidlyLeasedInEligibleOrder() {
		Instant now = Instant.parse("2026-11-02T01:00:00Z");
		row(5, now, 0, null, null, null);                                   // 경계 — 지금 평가할 수 있다
		row(4, now.minusSeconds(60), 0, null, null, null);                  // 더 일찍 평가할 수 있다
		row(3, now.plusSeconds(1), 0, null, null, null);                    // 아직 아니다
		row(2, now.minusSeconds(60), 5, null, null, "RETRIES_EXHAUSTED");  // 실패로 남았다
		row(6, now.minusSeconds(60), 0, OTHER, now.plusSeconds(60), null); // 다른 회차가 점유 중
		row(7, now.minusSeconds(30), 1, OTHER, now, null);                  // 점유가 지금 만료됐다

		assertThat(this.repository.lockDue(now, 10)).extracting(row -> row.articleId().value())
			.containsExactly(4L, 7L, 5L);
		assertThat(this.repository.lockDue(now, 1)).extracting(row -> row.articleId().value()).containsExactly(4L);
	}

	@Test
	void roundTripsEveryColumnThroughALockedSave() {
		row(8, T, 0, null, null, null);
		// 일시 실패 한 번 — 시도 1 · 평가 가능 00:01 · 문구가 남는다. 그 행을 다시 점유한다
		this.repository.saveLocked(this.repository.lockDue(T, 10).getFirst().claim(OTHER, T).afterTransient("시간 초과", T));
		PendingEvaluation retried = this.repository.lockDue(Instant.parse("2026-11-02T00:01:00Z"), 10).getFirst();

		this.repository.saveLocked(retried.claim(TOKEN, Instant.parse("2026-11-02T00:01:00Z")));

		assertThat(retried).isEqualTo(new PendingEvaluation(new ArticleId(8), Instant.parse("2026-11-02T00:01:00Z"), 1,
				null, null, "시간 초과"));
		assertThat(this.repository.lockDue(Instant.parse("2026-11-02T00:31:00Z"), 10))
			.containsExactly(new PendingEvaluation(new ArticleId(8), Instant.parse("2026-11-02T00:01:00Z"), 1,
					new EvaluationLease(TOKEN, Instant.parse("2026-11-02T00:31:00Z")), null, "시간 초과"));
	}

	@Test
	void appliesAResultOnlyWithTheLeaseToken() {
		row(9, T, 0, TOKEN, T.plusSeconds(1800), null);
		PendingEvaluation failed = new PendingEvaluation(new ArticleId(9), T, 0, null, QueueFailure.ARTICLE_REJECTED,
				"입력 거부");

		assertThat(this.repository.replaceIfLeased(OTHER, failed)).isFalse();
		assertThat(this.repository.deleteIfLeased(new ArticleId(9), OTHER)).isFalse();
		assertThat(this.repository.replaceIfLeased(TOKEN, failed)).isTrue();
		assertThat(failureOf(9)).isEqualTo(Map.of("failure", "ARTICLE_REJECTED", "last_error", "입력 거부"));

		row(10, T, 0, TOKEN, T.plusSeconds(1800), null);
		assertThat(this.repository.deleteIfLeased(new ArticleId(10), TOKEN)).isTrue();
		assertThat(count(10)).isZero();
	}

	@Test
	void deletesTheGivenArticlesAndCountsWaitingAndFailedRows() {
		row(11, T, 0, null, null, null);
		row(12, T, 2, TOKEN, T.plusSeconds(60), null);
		row(13, T, 1, null, null, "ARTICLE_REJECTED");
		row(14, T, 5, null, null, "RETRIES_EXHAUSTED");
		row(15, T, 5, null, null, "RETRIES_EXHAUSTED");

		assertThat(this.repository.counts()).isEqualTo(new EvaluationQueueCounts(2, 1, 2));

		this.repository.delete(List.of(new ArticleId(11), new ArticleId(15), new ArticleId(99)));
		this.repository.delete(List.of());

		assertThat(this.repository.counts()).isEqualTo(new EvaluationQueueCounts(1, 1, 1));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void aSecondRoundSkipsRowsLockedByTheFirst() throws InterruptedException {
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		row(20, T, 0, null, null, null);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			CompletableFuture<List<PendingEvaluation>> holder = CompletableFuture
				.supplyAsync(() -> transaction.execute(status -> {
					List<PendingEvaluation> due = this.repository.lockDue(T, 10);
					locked.countDown();
					awaitQuietly(release);
					return due;
				}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			// 앞 회차가 잡은 행은 기다리지 않고 건너뛴다 — 기다린다면 200ms 뒤 55P03 으로 실패한다
			List<PendingEvaluation> whileLocked = transaction.execute(status -> {
				this.jdbc.sql("set local lock_timeout = '200ms'").update();
				return this.repository.lockDue(T, 10);
			});
			release.countDown();
			List<PendingEvaluation> firstRound = holder.join();
			List<PendingEvaluation> afterRelease = transaction.execute(status -> this.repository.lockDue(T, 10));

			assertThat(firstRound).extracting(row -> row.articleId().value()).containsExactly(20L);
			assertThat(whileLocked).isEmpty();
			assertThat(afterRelease).extracting(row -> row.articleId().value()).containsExactly(20L);
		}
		finally {
			release.countDown();
			this.jdbc.sql("delete from evaluation_queue where article_id = 20").update();
		}
	}

	private void row(long article, Instant eligibleAt, int attempts, @Nullable UUID token, @Nullable Instant expiresAt,
			@Nullable String failure) {
		this.jdbc.sql("""
				insert into evaluation_queue (article_id, enqueued_at, eligible_at, attempts, lease_token, lease_expires_at,
				    failure)
				values (:article, :eligible, :eligible, :attempts, :token, :expires, :failure)
				""")
			.param("article", article)
			.param("eligible", eligibleAt.atOffset(java.time.ZoneOffset.UTC))
			.param("attempts", attempts)
			.param("token", token, java.sql.Types.OTHER)
			.param("expires", (expiresAt != null) ? expiresAt.atOffset(java.time.ZoneOffset.UTC) : null,
					java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
			.param("failure", failure, java.sql.Types.VARCHAR)
			.update();
	}

	private Map<String, Object> rowOf(long article) {
		return this.jdbc.sql("""
				select enqueued_at, eligible_at, attempts, lease_token, failure, last_error from evaluation_queue
				where article_id = :article
				""").param("article", article).query().singleRow();
	}

	private Map<String, Object> failureOf(long article) {
		return this.jdbc.sql("select failure, last_error from evaluation_queue where article_id = :article")
			.param("article", article)
			.query()
			.singleRow();
	}

	private long count(long article) {
		return this.jdbc.sql("select count(*) from evaluation_queue where article_id = :article")
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
