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
import net.yozmnews.digest.DigestRows;
import net.yozmnews.feedback.FeedbackRows;
import net.yozmnews.feedback.FeedbackValue;
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
 * 누르기 · 되돌리기 — 받을 수 있는 기사, 값이 바뀔 때만 쓰기, 같은 (사용자, 기사)의 차례.
 * <p>
 * 지키는 규칙: feedback-R1 ~ feedback-R4
 * <p>
 * 실제 PostgreSQL 과 실제 인용 확인({@code digest} 의 구현)으로 돈다. 기사 · 다이제스트 · 인용 행은 SQL 로 넣는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class FeedbackRecordingTests {

	private static final long USER = 42;

	private static final long OTHER = 43;

	private static final Instant NOW = Instant.parse("2026-11-17T01:00:00Z");

	@Autowired
	FeedbackRecording recording;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		FeedbackRows.clear(this.jdbc);
	}

	@Test
	void aChangedValueWritesTheStateAndOneHistoryRow() {
		long article = FeedbackRows.article(this.jdbc, "시험 기사 하나");
		long digest = citing(USER, "2026-11-01", article);
		long later = citing(USER, "2026-11-02", article);

		assertThat(record(USER, article, FeedbackValue.LIKE, digest)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
		assertThat(FeedbackRows.current(this.jdbc, USER)).containsExactly(article + ":LIKE:" + digest);
		assertThat(FeedbackRows.history(this.jdbc, USER, article)).containsExactly("LIKE:" + digest);

		// 같은 값을 다른 다이제스트에서 다시 — 아무것도 쓰지 않는다. 누른 다이제스트는 처음 것이다
		assertThat(record(USER, article, FeedbackValue.LIKE, later)).isEqualTo(new RecordOutcome.Unchanged(FeedbackValue.LIKE));
		assertThat(FeedbackRows.current(this.jdbc, USER)).containsExactly(article + ":LIKE:" + digest);
		assertThat(FeedbackRows.history(this.jdbc, USER, article)).containsExactly("LIKE:" + digest);

		assertThat(record(USER, article, FeedbackValue.DISLIKE, later))
			.isEqualTo(new RecordOutcome.Recorded(FeedbackValue.DISLIKE));
		assertThat(FeedbackRows.current(this.jdbc, USER)).containsExactly(article + ":DISLIKE:" + later);
		assertThat(FeedbackRows.history(this.jdbc, USER, article)).containsExactly("LIKE:" + digest, "DISLIKE:" + later);

		// 없음으로 되돌리기 — 현재 상태 행이 없어지고, 이력에 되돌리기를 누른 다이제스트가 남는다
		assertThat(record(USER, article, FeedbackValue.NONE, digest)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.NONE));
		assertThat(FeedbackRows.current(this.jdbc, USER)).isEmpty();
		assertThat(FeedbackRows.history(this.jdbc, USER, article)).containsExactly("LIKE:" + digest, "DISLIKE:" + later,
				"NONE:" + digest);
	}

	@Test
	void noneOnAnArticleWithoutStateWritesNothing() {
		long article = FeedbackRows.article(this.jdbc, "시험 기사 둘");
		long digest = citing(USER, "2026-11-01", article);

		assertThat(record(USER, article, FeedbackValue.NONE, digest)).isEqualTo(new RecordOutcome.Unchanged(FeedbackValue.NONE));
		assertThat(FeedbackRows.rows(this.jdbc)).isZero();
	}

	@Test
	void anArticleNotCitedByTheUsersDigestIsNotAccepted() {
		long article = FeedbackRows.article(this.jdbc, "시험 기사 셋");
		long elsewhere = FeedbackRows.article(this.jdbc, "시험 기사 넷");
		long mine = citing(USER, "2026-11-01", elsewhere);
		long theirs = citing(OTHER, "2026-11-01", article);

		// 내 다이제스트가 인용하지 않은 기사
		assertThat(record(USER, article, FeedbackValue.LIKE, mine)).isEqualTo(new RecordOutcome.NotInDigest());
		// 그 기사를 인용한 다른 사용자의 다이제스트
		assertThat(record(USER, article, FeedbackValue.LIKE, theirs)).isEqualTo(new RecordOutcome.NotInDigest());
		// 없는 다이제스트
		assertThat(record(USER, article, FeedbackValue.LIKE, theirs + 1000)).isEqualTo(new RecordOutcome.NotInDigest());
		// 되돌리기도 같은 확인을 지난다
		assertThat(record(USER, article, FeedbackValue.NONE, theirs)).isEqualTo(new RecordOutcome.NotInDigest());
		assertThat(FeedbackRows.rows(this.jdbc)).isZero();

		// 대조 — 인용한 기사는 받는다
		assertThat(record(USER, elsewhere, FeedbackValue.LIKE, mine)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
		assertThat(record(OTHER, article, FeedbackValue.LIKE, theirs))
			.isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
	}

	@Test
	void aDigestCoveredByALaterOneOfTheSameDateIsAccepted() {
		long article = FeedbackRows.article(this.jdbc, "시험 기사 다섯");
		long covered = citing(USER, "2026-11-01", article);
		citing(USER, "2026-11-01", FeedbackRows.article(this.jdbc, "시험 기사 여섯"));

		assertThat(record(USER, article, FeedbackValue.DISLIKE, covered))
			.isEqualTo(new RecordOutcome.Recorded(FeedbackValue.DISLIKE));
	}

	@Test
	void aCitedArticleThatNoLongerExistsIsNotAccepted() {
		// 인용은 남았는데 기사 행이 없다 — 지운 기사의 인용을 digest 가 아직 지우지 않은 틈
		long kept = FeedbackRows.article(this.jdbc, "시험 기사 일곱");
		long removed = FeedbackRows.article(this.jdbc, "시험 기사 여덟");
		long digest = citing(USER, "2026-11-01", kept);
		DigestRows.cite(this.jdbc, digest, removed);
		this.jdbc.sql("delete from article_feed_entries where article_id = :id").param("id", removed).update();
		this.jdbc.sql("delete from articles where id = :id").param("id", removed).update();

		assertThat(record(USER, removed, FeedbackValue.LIKE, digest)).isEqualTo(new RecordOutcome.NotInDigest());
		assertThat(FeedbackRows.rows(this.jdbc)).isZero();
		// 대조 — 같은 다이제스트의 남은 기사는 받는다
		assertThat(record(USER, kept, FeedbackValue.LIKE, digest)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
	}

	@Test
	void aSecondRequestForTheSameArticleWaitsForTheFirstAndLeavesOneHistoryRow() throws Exception {
		long article = FeedbackRows.article(this.jdbc, "시험 기사 아홉");
		long digest = citing(USER, "2026-11-01", article);
		CountDownLatch written = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		CompletableFuture<RecordOutcome> first = CompletableFuture.supplyAsync(() -> this.transaction.execute(status -> {
			RecordOutcome outcome = record(USER, article, FeedbackValue.LIKE, digest);
			written.countDown();
			Latches.awaitQuietly(release);
			return outcome;
		}));
		Latches.awaitQuietly(written);
		CompletableFuture<RecordOutcome> second = CompletableFuture
			.supplyAsync(() -> record(USER, article, FeedbackValue.LIKE, digest));

		// 앞 요청이 커밋하기 전에는 뒤 요청이 끝나지 않는다 — 행 잠금에서 기다린다
		assertThatThrownBy(() -> second.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
		release.countDown();

		assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
		assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(new RecordOutcome.Unchanged(FeedbackValue.LIKE));
		assertThat(FeedbackRows.history(this.jdbc, USER, article)).containsExactly("LIKE:" + digest);
	}

	@Test
	void theLastHistoryRowOfEachArticleIsItsCurrentState() {
		long liked = FeedbackRows.article(this.jdbc, "시험 기사 열");
		long cleared = FeedbackRows.article(this.jdbc, "시험 기사 열하나");
		long disliked = FeedbackRows.article(this.jdbc, "시험 기사 열둘");
		long digest = citing(USER, "2026-11-01", liked);
		DigestRows.cite(this.jdbc, digest, cleared);
		DigestRows.cite(this.jdbc, digest, disliked);

		record(USER, liked, FeedbackValue.DISLIKE, digest);
		record(USER, liked, FeedbackValue.LIKE, digest);
		record(USER, cleared, FeedbackValue.LIKE, digest);
		record(USER, cleared, FeedbackValue.NONE, digest);
		record(USER, disliked, FeedbackValue.LIKE, digest);
		record(USER, disliked, FeedbackValue.DISLIKE, digest);

		assertThat(FeedbackRows.current(this.jdbc, USER)).containsExactly(liked + ":LIKE:" + digest,
				disliked + ":DISLIKE:" + digest);
		assertThat(FeedbackRows.history(this.jdbc, USER, liked)).last().isEqualTo("LIKE:" + digest);
		assertThat(FeedbackRows.history(this.jdbc, USER, cleared)).last().isEqualTo("NONE:" + digest);
		assertThat(FeedbackRows.history(this.jdbc, USER, disliked)).last().isEqualTo("DISLIKE:" + digest);
	}

	private RecordOutcome record(long user, long article, FeedbackValue value, long digest) {
		return this.recording.record(new UserId(user), new ArticleId(article), value, digest, NOW);
	}

	private long citing(long user, String date, long article) {
		long digest = DigestRows.digest(this.jdbc, user, date);
		DigestRows.cite(this.jdbc, digest, article);
		return digest;
	}

}
