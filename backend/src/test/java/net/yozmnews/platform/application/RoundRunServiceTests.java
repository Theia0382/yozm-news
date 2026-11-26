package net.yozmnews.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

import net.yozmnews.TestClocks;
import net.yozmnews.TestClocks.MovableClock;
import net.yozmnews.platform.BatchEnd;
import net.yozmnews.platform.RoundBatch;
import net.yozmnews.platform.RoundEnd;
import net.yozmnews.platform.RoundSummary;
import net.yozmnews.platform.domain.RoundRun;
import net.yozmnews.platform.domain.RoundRunRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 회차 실행 — 뭉치를 끝 조건까지 되풀이하고 회차 기록 한 행을 남긴다. 시계는 뭉치마다 앞으로 가는 측정용 시계의 흉내다.
 * <p>
 * 지키는 규칙: platform-R130 · platform-R131 · platform-R132
 */
@ExtendWith(OutputCaptureExtension.class)
class RoundRunServiceTests {

	static final Instant T0 = Instant.parse("2026-11-26T12:00:00Z");

	final MovableClock clock = TestClocks.movable(T0);

	final Runs runs = new Runs();

	final RoundRunService service = new RoundRunService(this.runs, this.clock);

	@Test
	void fullBatchesRepeatUntilOneDrainsTheQueue() {
		this.service.run("storyline-linking", batches(RoundBatch.finished(32, 32), RoundBatch.finished(32, 32),
				RoundBatch.finished(1, 32)));

		assertThat(this.runs.inserted)
			.containsExactly(new RoundRun("storyline-linking", T0, T0, 3, 65, RoundEnd.DRAINED));
	}

	@Test
	void aRoundThatFoundNothingIsRecordedToo() {
		this.service.run("event-merge", batches(RoundBatch.finished(0, 16)));

		assertThat(this.runs.inserted).containsExactly(new RoundRun("event-merge", T0, T0, 1, 0, RoundEnd.DRAINED));
	}

	@Test
	void timeTheClockSkippedDoesNotUseTheBudget() {
		// 첫 뭉치 동안 시계를 1시간 넘긴다 — 넘긴 시간을 뺀 경과는 0 이라 다음 뭉치를 가져온다
		Deque<RoundBatch> left = new ArrayDeque<>(List.of(RoundBatch.finished(32, 32), RoundBatch.finished(3, 32)));
		this.service.run("storyline-linking", () -> {
			if (left.size() == 2) {
				this.clock.jump(Duration.ofHours(1));
			}
			return left.removeFirst();
		});

		assertThat(this.runs.inserted).containsExactly(new RoundRun("storyline-linking", T0,
				Instant.parse("2026-11-26T13:00:00Z"), 2, 35, RoundEnd.DRAINED));
	}

	@Test
	void fullBatchesStopTakingWorkOnceFiveMinutesHavePassed() {
		// 뭉치마다 2분 — 경과 2 · 4분에는 이어 가고 6분에 끝낸다
		Deque<RoundBatch> full = new ArrayDeque<>(List.of(RoundBatch.finished(4, 4), RoundBatch.finished(4, 4),
				RoundBatch.finished(4, 4), RoundBatch.finished(4, 4)));
		this.service.run("storyline-relation", () -> {
			this.clock.advance(Duration.ofMinutes(2));
			return full.removeFirst();
		});

		assertThat(this.runs.inserted).containsExactly(
				new RoundRun("storyline-relation", T0, Instant.parse("2026-11-26T12:06:00Z"), 3, 12, RoundEnd.BUDGET));
		assertThat(full).hasSize(1);
	}

	@Test
	void aBatchWithATransientFailureEndsTheRound() {
		this.service.run("event-assignment", batches(RoundBatch.backedOff(32), RoundBatch.finished(32, 32)));

		assertThat(this.runs.inserted).containsExactly(new RoundRun("event-assignment", T0, T0, 1, 32, RoundEnd.BACKED_OFF));
	}

	@Test
	void aStoppedBatchEndsTheRound() {
		this.service.run("tag-evaluation", batches(RoundBatch.finished(50, 50), RoundBatch.stopped(7),
				RoundBatch.finished(50, 50)));

		assertThat(this.runs.inserted).containsExactly(new RoundRun("tag-evaluation", T0, T0, 2, 57, RoundEnd.STOPPED));
	}

	@Test
	void aThrowingBatchIsRecordedAsFailedAndTheExceptionGoesOn() {
		IllegalStateException boom = new IllegalStateException("뭉치 실패");
		Deque<RoundBatch> first = new ArrayDeque<>(List.of(RoundBatch.finished(64, 64)));
		Supplier<RoundBatch> batch = () -> {
			if (first.isEmpty()) {
				this.clock.advance(Duration.ofSeconds(3));
				throw boom;
			}
			return first.removeFirst();
		};

		assertThatThrownBy(() -> this.service.run("event-features", batch)).isSameAs(boom);
		assertThat(this.runs.inserted).containsExactly(new RoundRun("event-features", T0,
				Instant.parse("2026-11-26T12:00:03Z"), 1, 64, RoundEnd.FAILED));
	}

	@Test
	void aFirstBatchThatThrowsLeavesARunWithNoBatches() {
		IllegalStateException boom = new IllegalStateException("뭉치 실패");

		assertThatThrownBy(() -> this.service.run("event-merge", () -> {
			throw boom;
		})).isSameAs(boom);
		assertThat(this.runs.inserted).containsExactly(new RoundRun("event-merge", T0, T0, 0, 0, RoundEnd.FAILED));
	}

	@Test
	void aWrongNameIsRefusedBeforeAnyBatch() {
		List<String> called = new ArrayList<>();

		assertThatIllegalArgumentException().isThrownBy(() -> this.service.run("Event_Merge", () -> {
			called.add("batch");
			return RoundBatch.finished(0, 16);
		}));
		assertThat(called).isEmpty();
		assertThat(this.runs.inserted).isEmpty();
	}

	@Test
	void aRecordThatCannotBeWrittenIsLoggedAndTheRoundStillEnds(CapturedOutput output) {
		this.runs.failInsert = new IllegalStateException("DB 없음");

		this.service.run("storyline-linking", batches(RoundBatch.finished(3, 32)));

		assertThat(output).contains("회차 기록을 남기지 못했다 — storyline-linking DRAINED, 뭉치 1개 · 처리 3건");
	}

	@Test
	void aRecordThatCannotBeMadeIsLoggedAndTheRoundStillEnds(CapturedOutput output) {
		// 뭉치 동안 시계가 1초 뒤로 간다(실제 시간 시계의 조정) — 끝난 시각이 시작보다 앞선 기록은 만들 수 없다
		this.service.run("event-features", () -> {
			this.clock.advance(Duration.ofSeconds(-1));
			return RoundBatch.finished(2, 64);
		});

		assertThat(this.runs.inserted).isEmpty();
		assertThat(output).contains("회차 기록을 남기지 못했다 — event-features DRAINED, 뭉치 1개 · 처리 2건");
	}

	@Test
	void aFailedRecordThatCannotBeWrittenRidesOnTheBatchException() {
		IllegalStateException boom = new IllegalStateException("뭉치 실패");
		IllegalStateException noDb = new IllegalStateException("DB 없음");
		this.runs.failInsert = noDb;

		assertThatThrownBy(() -> this.service.run("event-merge", () -> {
			throw boom;
		})).isSameAs(boom).satisfies(ex -> assertThat(ex.getSuppressed()).containsExactly(noDb));
	}

	@Test
	void recentReadsTheLastHourFromNow() {
		this.clock.advance(Duration.ofMinutes(90));

		assertThat(this.service.recent("storyline-relation")).isEqualTo(RoundSummary.NONE);
		assertThat(this.runs.asked).containsExactly("storyline-relation@2026-11-26T12:30:00Z");
	}

	private static Supplier<RoundBatch> batches(RoundBatch... results) {
		Deque<RoundBatch> left = new ArrayDeque<>(List.of(results));
		return left::removeFirst;
	}

	/** 남긴 기록과 물은 창을 모으는 저장소 */
	static final class Runs implements RoundRunRepository {

		final List<RoundRun> inserted = new ArrayList<>();

		final List<String> asked = new ArrayList<>();

		@Nullable RuntimeException failInsert;

		@Override
		public void insert(RoundRun run) {
			if (this.failInsert != null) {
				throw this.failInsert;
			}
			this.inserted.add(run);
		}

		@Override
		public RoundSummary recent(String round, Instant since) {
			this.asked.add(round + "@" + since);
			return RoundSummary.NONE;
		}

		@Override
		public int deleteEndedBefore(Instant before) {
			throw new UnsupportedOperationException();
		}

	}

}
