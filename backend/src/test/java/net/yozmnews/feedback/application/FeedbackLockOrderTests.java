package net.yozmnews.feedback.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import net.yozmnews.Latches;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.EventId;
import net.yozmnews.feedback.DigestItems;
import net.yozmnews.feedback.FeedbackRows;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.feedback.domain.record.FeedbackRepository;
import net.yozmnews.feedback.domain.record.HistoryRow;
import net.yozmnews.feedback.domain.record.ItemRecordRepository;
import net.yozmnews.feedback.domain.record.LastChange;
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
 * 누르기 한 번의 잠금 순서 — 같은 사건을 담은 서로 다른 항목을 동시에 눌러도 교착하지 않는다(사건 행은 사건 ID 오름차순으로 잠근다).
 * <p>
 * 지키는 규칙: feedback-R4
 * <p>
 * 실제 PostgreSQL 위에서 돈다. 맞물림은 결정적으로 만든다 — 저장소를 감싼 대역이 첫 사건 행을 쓴 직후 앞 요청을 멈추고, 그동안 뒤 요청이 자기
 * 잠금 순서대로 나아갈 만큼 기다린 뒤 앞 요청을 놓는다. 항목 위치 순으로 잠그면 뒤 요청({e2, e1})이 e2 를 쥔 채 e1 을 기다리고 앞 요청({e1,
 * e2})이 e1 을 쥔 채 e2 를 기다려 교착이 된다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class FeedbackLockOrderTests {

	private static final long USER = 42;

	private static final Instant NOW = Instant.parse("2026-11-17T01:00:00Z");

	@Autowired
	RemovalFence fence;

	@Autowired
	DigestItems digestItems;

	@Autowired
	EventResolution resolution;

	@Autowired
	ItemRecordRepository itemRecords;

	@Autowired
	FeedbackRepository feedback;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		FeedbackRows.clear(this.jdbc);
	}

	@Test
	void twoItemsSharingEventsInOppositePositionsDoNotDeadlock() throws Exception {
		long e1 = FeedbackRows.event(this.jdbc);
		long e2 = FeedbackRows.event(this.jdbc);
		long forward = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", e1, e2);
		long backward = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-02", e2, e1);

		Outcomes outcomes = pressBoth(e1, forward, e2, backward);

		assertThat(outcomes.first()).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
		assertThat(outcomes.second()).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.DISLIKE));
		// 뒤 요청이 나중에 커밋됐다 — 사건마다 마지막 이력 값이 현재 상태다
		assertThat(FeedbackRows.current(this.jdbc, USER)).containsExactly(e1 + ":DISLIKE:" + backward,
				e2 + ":DISLIKE:" + backward);
		assertThat(FeedbackRows.history(this.jdbc, USER, e1)).containsExactly("LIKE:" + forward,
				"DISLIKE:" + backward);
		assertThat(FeedbackRows.history(this.jdbc, USER, e2)).containsExactly("LIKE:" + forward,
				"DISLIKE:" + backward);
	}

	@Test
	void twoItemsWithoutSharedEventsDoNotWaitForEachOther() throws Exception {
		// 대조 — 사건이 겹치지 않으면 앞 요청이 멈춘 동안에도 뒤 요청이 끝난다(위의 기다림이 "언제나 기다린다"여서가 아니다)
		long e1 = FeedbackRows.event(this.jdbc);
		long e2 = FeedbackRows.event(this.jdbc);
		long e3 = FeedbackRows.event(this.jdbc);
		long e4 = FeedbackRows.event(this.jdbc);
		long forward = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-01", e1, e2);
		long other = FeedbackRows.digestWithItem(this.jdbc, USER, "2026-11-02", e4, e3);
		CountDownLatch paused = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		FeedbackRecording pausing = recording(paused, release);

		CompletableFuture<RecordOutcome> first = CompletableFuture
			.supplyAsync(() -> this.transaction.execute(status -> press(pausing, e1, FeedbackValue.LIKE, forward)));
		Latches.awaitQuietly(paused);
		CompletableFuture<RecordOutcome> second = CompletableFuture
			.supplyAsync(() -> this.transaction.execute(status -> press(pausing, e4, FeedbackValue.DISLIKE, other)));

		assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.DISLIKE));
		release.countDown();
		assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(new RecordOutcome.Recorded(FeedbackValue.LIKE));
	}

	/**
	 * 앞 요청(항목 {@code firstKey})이 첫 사건 행을 쓴 직후 멈추고, 뒤 요청(항목 {@code secondKey})이 그 행에서 기다리는 것을 본 뒤 앞 요청을
	 * 놓는다
	 */
	private Outcomes pressBoth(long firstKey, long firstDigest, long secondKey, long secondDigest) throws Exception {
		CountDownLatch paused = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		FeedbackRecording pausing = recording(paused, release);

		CompletableFuture<RecordOutcome> first = CompletableFuture.supplyAsync(
				() -> this.transaction.execute(status -> press(pausing, firstKey, FeedbackValue.LIKE, firstDigest)));
		Latches.awaitQuietly(paused);
		CompletableFuture<RecordOutcome> second = CompletableFuture.supplyAsync(() -> this.transaction
			.execute(status -> press(pausing, secondKey, FeedbackValue.DISLIKE, secondDigest)));

		// 앞 요청이 첫 사건 행을 쥔 동안 뒤 요청은 끝나지 않는다 — 그 사이 뒤 요청은 자기 순서대로 잡을 수 있는 행을 잡는다
		assertThatThrownBy(() -> second.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
		release.countDown();
		return new Outcomes(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
	}

	private FeedbackRecording recording(CountDownLatch paused, CountDownLatch release) {
		return new FeedbackRecording(this.fence, this.digestItems, this.resolution, this.itemRecords,
				new PausingFeedback(this.feedback, paused, release));
	}

	private static RecordOutcome press(FeedbackRecording recording, long event, FeedbackValue value, long digest) {
		return recording.record(new UserId(USER), new EventId(event), value, digest, NOW);
	}

	private record Outcomes(RecordOutcome first, RecordOutcome second) {
	}

	/** 처음 부른 사건 행 쓰기 뒤에 한 번 멈추는 저장소 — 나머지는 실제 저장소에 넘긴다 */
	private static final class PausingFeedback implements FeedbackRepository {

		private final FeedbackRepository delegate;

		private final CountDownLatch paused;

		private final CountDownLatch release;

		private final AtomicBoolean pausedOnce = new AtomicBoolean();

		PausingFeedback(FeedbackRepository delegate, CountDownLatch paused, CountDownLatch release) {
			this.delegate = delegate;
			this.paused = paused;
			this.release = release;
		}

		@Override
		public boolean change(UserId user, EventId event, FeedbackValue value, long digestId, Instant now) {
			boolean changed = this.delegate.change(user, event, value, digestId, now);
			if (this.pausedOnce.compareAndSet(false, true)) {
				this.paused.countDown();
				Latches.awaitQuietly(this.release);
			}
			return changed;
		}

		@Override
		public void append(UserId user, EventId event, FeedbackValue value, long digestId, Instant now) {
			this.delegate.append(user, event, value, digestId, now);
		}

		@Override
		public List<LastChange> lastChanges(UserId user, Collection<EventId> events) {
			return this.delegate.lastChanges(user, events);
		}

		@Override
		public List<HistoryRow> history(UserId user, Instant from, Instant to) {
			return this.delegate.history(user, from, to);
		}

		@Override
		public void removeAll(Collection<EventId> events) {
			this.delegate.removeAll(events);
		}

	}

}
