package net.yozmnews.feedback.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.yozmnews.Latches;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.DigestRows;
import net.yozmnews.event.EventId;
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
 * 누르기 · 되돌리기 — 받을 수 있는 항목, 항목에 한 번 누르면 항목 기록 하나와 항목의 사건마다 기록, 값이 바뀔 때만 쓰기, 같은 항목의 차례.
 * <p>
 * 지키는 규칙: feedback-R1 ~ feedback-R4
 * <p>
 * 실제 PostgreSQL 과 실제 항목 확인({@code digest} 의 구현) · 실제 사건 풀기({@code event})로 돈다. 사건 · 다이제스트 · 섹션 사건 행은 SQL
 * 로 넣는다.
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
	void pressingAnItemRecordsTheItemOnceAndEachOfItsEvents() {
		long e1 = FeedbackRows.event(this.jdbc);
		long e2 = FeedbackRows.event(this.jdbc);
		long digest = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", e1, e2);

		assertThat(record(USER, e1, FeedbackValue.LIKE, digest)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
		assertThat(FeedbackRows.items(this.jdbc, USER)).containsExactly(digest + ":" + e1 + ":LIKE");
		assertThat(FeedbackRows.current(this.jdbc, USER)).containsExactly(e1 + ":LIKE:" + digest, e2 + ":LIKE:" + digest);
		assertThat(FeedbackRows.history(this.jdbc, USER, e1)).containsExactly("LIKE:" + digest);
		assertThat(FeedbackRows.history(this.jdbc, USER, e2)).containsExactly("LIKE:" + digest);

		// 항목의 둘째 사건으로 눌러도 같은 항목이다 — 항목 기록의 키는 첫 사건이다
		assertThat(record(USER, e2, FeedbackValue.DISLIKE, digest))
			.isEqualTo(new RecordOutcome.Recorded(FeedbackValue.DISLIKE));
		assertThat(FeedbackRows.items(this.jdbc, USER)).containsExactly(digest + ":" + e1 + ":DISLIKE");
		assertThat(FeedbackRows.current(this.jdbc, USER)).containsExactly(e1 + ":DISLIKE:" + digest,
				e2 + ":DISLIKE:" + digest);
		assertThat(FeedbackRows.history(this.jdbc, USER, e2)).containsExactly("LIKE:" + digest, "DISLIKE:" + digest);
	}

	@Test
	void anEventAlreadyAtTheValueAddsNoHistory() {
		// 대조 — 사건 하나짜리 항목. 이미 그 값인 사건은 쓰지 않고, 모두 그 값이면 결과는 바뀌지 않음이다
		long event = FeedbackRows.event(this.jdbc);
		long first = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", event);
		long second = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-02", event);
		record(USER, event, FeedbackValue.LIKE, first);

		// 뒤 항목의 기록만 생긴다 — 사건은 처음 누른 다이제스트 그대로이고 이력이 늘지 않는다
		assertThat(record(USER, event, FeedbackValue.LIKE, second))
			.isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
		assertThat(FeedbackRows.current(this.jdbc, USER)).containsExactly(event + ":LIKE:" + first);
		assertThat(FeedbackRows.history(this.jdbc, USER, event)).containsExactly("LIKE:" + first);

		assertThat(record(USER, event, FeedbackValue.LIKE, second))
			.isEqualTo(new RecordOutcome.Unchanged(FeedbackValue.LIKE));
		assertThat(FeedbackRows.history(this.jdbc, USER, event)).containsExactly("LIKE:" + first);
	}

	@Test
	void anItemWithAnEventAlreadyAtTheValueWritesOnlyItsOtherEvents() {
		long e1 = FeedbackRows.event(this.jdbc);
		long e2 = FeedbackRows.event(this.jdbc);
		long first = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", e1);
		long second = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-02", e1, e2);
		record(USER, e1, FeedbackValue.LIKE, first);

		assertThat(record(USER, e1, FeedbackValue.LIKE, second)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
		// e1 은 처음 누른 다이제스트 그대로이고 이력이 늘지 않는다
		assertThat(FeedbackRows.current(this.jdbc, USER)).containsExactly(e1 + ":LIKE:" + first, e2 + ":LIKE:" + second);
		assertThat(FeedbackRows.history(this.jdbc, USER, e1)).containsExactly("LIKE:" + first);
		assertThat(FeedbackRows.history(this.jdbc, USER, e2)).containsExactly("LIKE:" + second);

		assertThat(record(USER, e2, FeedbackValue.LIKE, second))
			.isEqualTo(new RecordOutcome.Unchanged(FeedbackValue.LIKE));
		assertThat(FeedbackRows.history(this.jdbc, USER, e1)).containsExactly("LIKE:" + first);
		assertThat(FeedbackRows.history(this.jdbc, USER, e2)).containsExactly("LIKE:" + second);
	}

	@Test
	void revertingClearsEveryEventOfTheItemIncludingAValueLeftFromAnotherItem() {
		long e1 = FeedbackRows.event(this.jdbc);
		long e2 = FeedbackRows.event(this.jdbc);
		long earlier = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", e1);
		long later = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-02", e1, e2);
		record(USER, e1, FeedbackValue.LIKE, earlier);

		// 뒤 항목에는 누른 값이 없어도 되돌리기는 그 항목의 사건 모두를 없음으로 둔다
		assertThat(record(USER, e2, FeedbackValue.NONE, later)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.NONE));
		assertThat(FeedbackRows.current(this.jdbc, USER)).isEmpty();
		assertThat(FeedbackRows.history(this.jdbc, USER, e1)).containsExactly("LIKE:" + earlier, "NONE:" + later);
		assertThat(FeedbackRows.history(this.jdbc, USER, e2)).isEmpty();
		// 앞 항목에 누른 값은 그 항목의 기록으로 남는다
		assertThat(FeedbackRows.items(this.jdbc, USER)).containsExactly(earlier + ":" + e1 + ":LIKE");
		// 되돌릴 값이 없으면 아무것도 쓰지 않는다
		assertThat(record(USER, e2, FeedbackValue.NONE, later)).isEqualTo(new RecordOutcome.Unchanged(FeedbackValue.NONE));
		assertThat(FeedbackRows.history(this.jdbc, USER, e1)).containsExactly("LIKE:" + earlier, "NONE:" + later);
	}

	@Test
	void revertingClearsTheItemRecord() {
		// 대조 — 항목 기록의 되돌리기. 사건 기록은 위 시험이 본다
		long e1 = FeedbackRows.event(this.jdbc);
		long digest = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", e1);
		record(USER, e1, FeedbackValue.DISLIKE, digest);

		assertThat(record(USER, e1, FeedbackValue.NONE, digest)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.NONE));
		assertThat(FeedbackRows.items(this.jdbc, USER)).isEmpty();
	}

	@Test
	void anItemNotFoundInTheUsersDigestIsNotAccepted() {
		long shown = FeedbackRows.event(this.jdbc);
		long unseen = FeedbackRows.event(this.jdbc);
		long mine = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", shown);
		long theirs = FeedbackRows.digestWithItem(this.jdbc, OTHER, "2026-11-01", unseen);

		// 내 다이제스트가 보이지 않은 사건
		assertThat(record(USER, unseen, FeedbackValue.LIKE, mine)).isEqualTo(new RecordOutcome.NotInDigest());
		// 그 사건을 보인 다른 사용자의 다이제스트
		assertThat(record(USER, unseen, FeedbackValue.LIKE, theirs)).isEqualTo(new RecordOutcome.NotInDigest());
		// 없는 다이제스트
		assertThat(record(USER, unseen, FeedbackValue.LIKE, theirs + 1000)).isEqualTo(new RecordOutcome.NotInDigest());
		// 되돌리기도 같은 확인을 지난다
		assertThat(record(USER, unseen, FeedbackValue.NONE, theirs)).isEqualTo(new RecordOutcome.NotInDigest());
		assertThat(FeedbackRows.rows(this.jdbc)).isZero();

		// 대조 — 보인 사건의 항목은 받는다
		assertThat(record(USER, shown, FeedbackValue.LIKE, mine)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
		assertThat(record(OTHER, unseen, FeedbackValue.LIKE, theirs))
			.isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
	}

	@Test
	void aDigestCoveredByALaterOneOfTheSameDateIsAccepted() {
		long event = FeedbackRows.event(this.jdbc);
		long covered = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", event);
		FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", FeedbackRows.event(this.jdbc));

		assertThat(record(USER, event, FeedbackValue.DISLIKE, covered))
			.isEqualTo(new RecordOutcome.Recorded(FeedbackValue.DISLIKE));
	}

	@Test
	void aRemovedEventOfTheItemGetsNoEventRowWhileTheItemIsRecorded() {
		long kept = FeedbackRows.event(this.jdbc);
		long removed = FeedbackRows.event(this.jdbc);
		long digest = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", kept, removed);
		FeedbackRows.deleteEvent(this.jdbc, removed);

		assertThat(record(USER, removed, FeedbackValue.LIKE, digest))
			.isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
		assertThat(FeedbackRows.items(this.jdbc, USER)).containsExactly(digest + ":" + kept + ":LIKE");
		assertThat(FeedbackRows.current(this.jdbc, USER)).containsExactly(kept + ":LIKE:" + digest);
		assertThat(FeedbackRows.history(this.jdbc, USER, removed)).isEmpty();
	}

	@Test
	void aMergedEventIsRecordedUnderItsOriginalId() {
		long merged = FeedbackRows.event(this.jdbc);
		long survivor = FeedbackRows.event(this.jdbc);
		long digest = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", merged);
		FeedbackRows.merge(this.jdbc, merged, survivor);

		assertThat(record(USER, merged, FeedbackValue.DISLIKE, digest))
			.isEqualTo(new RecordOutcome.Recorded(FeedbackValue.DISLIKE));
		assertThat(FeedbackRows.current(this.jdbc, USER)).containsExactly(merged + ":DISLIKE:" + digest);
		assertThat(FeedbackRows.history(this.jdbc, USER, survivor)).isEmpty();
	}

	@Test
	void aSecondRequestForTheSameItemWaitsForTheFirstAndLeavesOneHistoryRow() throws Exception {
		long event = FeedbackRows.event(this.jdbc);
		long digest = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", event);
		CountDownLatch written = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		CompletableFuture<RecordOutcome> first = CompletableFuture.supplyAsync(() -> this.transaction.execute(status -> {
			RecordOutcome outcome = record(USER, event, FeedbackValue.LIKE, digest);
			written.countDown();
			Latches.awaitQuietly(release);
			return outcome;
		}));
		Latches.awaitQuietly(written);
		CompletableFuture<RecordOutcome> second = CompletableFuture
			.supplyAsync(() -> record(USER, event, FeedbackValue.LIKE, digest));

		// 앞 요청이 커밋하기 전에는 뒤 요청이 끝나지 않는다 — 항목 기록 행 잠금에서 기다린다
		assertThatThrownBy(() -> second.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
		release.countDown();

		assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
		assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(new RecordOutcome.Unchanged(FeedbackValue.LIKE));
		assertThat(FeedbackRows.history(this.jdbc, USER, event)).containsExactly("LIKE:" + digest);
	}

	@Test
	void theLastHistoryRowOfEachEventIsItsCurrentState() {
		long liked = FeedbackRows.event(this.jdbc);
		long cleared = FeedbackRows.event(this.jdbc);
		long disliked = FeedbackRows.event(this.jdbc);
		long digest = DigestRows.digest(this.jdbc, USER, "2026-11-01");
		DigestRows.item(this.jdbc, digest, liked);
		DigestRows.item(this.jdbc, digest, cleared);
		DigestRows.item(this.jdbc, digest, disliked);

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
		assertThat(FeedbackRows.items(this.jdbc, USER)).containsExactly(digest + ":" + liked + ":LIKE",
				digest + ":" + disliked + ":DISLIKE");
	}

	private RecordOutcome record(long user, long event, FeedbackValue value, long digest) {
		return this.recording.record(new UserId(user), new EventId(event), value, digest, NOW);
	}

}
