package net.yozmnews.feedback.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.yozmnews.Latches;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.digest.DigestRows;
import net.yozmnews.feedback.FeedbackRows;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.feedback.domain.record.RemovalFence;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지운 기사 따르기 — 그 기사의 현재 상태 · 이력 행을 지우고, 진행 중인 기록을 기다린다.
 * <p>
 * 지키는 규칙: feedback-R5 · feedback-R6
 * <p>
 * 실제 PostgreSQL 위에서 돈다. 기록이 진행 중인 틈은 트랜잭션 안에서 래치로 멈춰 만든다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class FeedbackRemovalTests {

	@Autowired
	FeedbackRemoval removal;

	@Autowired
	FeedbackRecording recording;

	@Autowired
	RemovalFence fence;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		FeedbackRows.clear(this.jdbc);
	}

	@Test
	void theRemovedArticlesRowsAreDeletedAndOthersStay() {
		rows(42, 901);
		rows(43, 901);
		rows(42, 902);
		rows(43, 903);

		new FeedbackArticleRemovalListener(this.removal).on(new ArticlesRemoved(List.of(new ArticleId(901))));

		assertThat(articles("feedback")).containsExactly("42:902", "43:903");
		assertThat(articles("feedback_events")).containsExactly("42:902", "43:903");

		// 다시 처리해도 결과가 같다
		new FeedbackArticleRemovalListener(this.removal).on(new ArticlesRemoved(List.of(new ArticleId(901))));
		assertThat(articles("feedback")).containsExactly("42:902", "43:903");
		assertThat(articles("feedback_events")).containsExactly("42:902", "43:903");
	}

	@Test
	void removalWaitsForARecordingThatHoldsTheFence() throws Exception {
		rows(42, 901);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> recording = CompletableFuture.runAsync(() -> this.transaction.executeWithoutResult(status -> {
			this.fence.enterShared();
			entered.countDown();
			Latches.awaitQuietly(release);
		}));
		Latches.awaitQuietly(entered);

		CompletableFuture<Void> removing = CompletableFuture
			.runAsync(() -> this.removal.apply(List.of(new ArticleId(901))));

		// 기록이 울타리를 쥔 동안 삭제는 끝나지 않는다
		assertThatThrownBy(() -> removing.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
		assertThat(articles("feedback")).containsExactly("42:901");
		release.countDown();
		recording.get(30, TimeUnit.SECONDS);
		removing.get(30, TimeUnit.SECONDS);
		assertThat(articles("feedback")).isEmpty();
		assertThat(articles("feedback_events")).isEmpty();
	}

	@Test
	void removalWaitsForAnUncommittedRecordingAndThenDeletesItsRows() throws Exception {
		// 기록 유스케이스가 울타리를 실제로 잡는지 — 위 시험은 울타리를 직접 잡는다
		long article = FeedbackRows.article(this.jdbc, "시험 기사 하나");
		long digest = DigestRows.digest(this.jdbc, 42, "2026-11-01");
		DigestRows.cite(this.jdbc, digest, article);
		CountDownLatch written = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<RecordOutcome> recorded = CompletableFuture
			.supplyAsync(() -> this.transaction.execute(status -> {
				RecordOutcome outcome = this.recording.record(new UserId(42), new ArticleId(article),
						FeedbackValue.LIKE, digest, Instant.parse("2026-11-17T01:00:00Z"));
				written.countDown();
				Latches.awaitQuietly(release);
				return outcome;
			}));
		Latches.awaitQuietly(written);

		CompletableFuture<Void> removing = CompletableFuture
			.runAsync(() -> this.removal.apply(List.of(new ArticleId(article))));

		// 기록이 커밋하기 전에는 삭제가 끝나지 않는다
		assertThatThrownBy(() -> removing.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
		release.countDown();
		assertThat(recorded.get(30, TimeUnit.SECONDS)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
		removing.get(30, TimeUnit.SECONDS);
		// 먼저 커밋된 기록의 행을 삭제가 지운다
		assertThat(articles("feedback")).isEmpty();
		assertThat(articles("feedback_events")).isEmpty();
	}

	@Test
	void recordingsShareTheFence() throws Exception {
		// 대조 — 공유끼리는 기다리지 않는다(위의 기다림이 "울타리가 언제나 막는다"여서가 아니다)
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> this.transaction.executeWithoutResult(status -> {
			this.fence.enterShared();
			entered.countDown();
			Latches.awaitQuietly(release);
		}));
		Latches.awaitQuietly(entered);

		CompletableFuture<Void> other = CompletableFuture
			.runAsync(() -> this.transaction.executeWithoutResult(status -> this.fence.enterShared()));
		other.get(10, TimeUnit.SECONDS);
		release.countDown();
		holder.get(30, TimeUnit.SECONDS);
	}

	private void rows(long user, long article) {
		this.jdbc.sql("""
				insert into feedback (user_id, article_id, value, digest_id, updated_at)
				values (:user, :article, 'LIKE', 1, now())
				""").param("user", user).param("article", article).update();
		this.jdbc.sql("""
				insert into feedback_events (user_id, article_id, value, digest_id, occurred_at)
				values (:user, :article, 'LIKE', 1, now())
				""").param("user", user).param("article", article).update();
	}

	private List<String> articles(String table) {
		return this.jdbc.sql("select user_id, article_id from " + table + " order by user_id, article_id")
			.query((rs, row) -> rs.getLong("user_id") + ":" + rs.getLong("article_id"))
			.list();
	}

}
