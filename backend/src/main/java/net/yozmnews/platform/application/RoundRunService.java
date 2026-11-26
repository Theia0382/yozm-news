package net.yozmnews.platform.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.RoundActivity;
import net.yozmnews.platform.RoundBatch;
import net.yozmnews.platform.RoundEnd;
import net.yozmnews.platform.RoundRunner;
import net.yozmnews.platform.RoundSummary;
import net.yozmnews.platform.domain.RoundRules;
import net.yozmnews.platform.domain.RoundRun;
import net.yozmnews.platform.domain.RoundRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 회차 실행과 회차 기록 조회 — docs/specs/platform.md §4 · 반복 회차.
 * <p>
 * 뭉치를 {@link RoundRules#endAfter} 가 끝내라 할 때까지 부르고, 끝나면 회차 기록 한 행을 남긴다. 경과는 앱 시계의 경과(측정용 시계가 넘긴 시간을
 * 뺀다)다. 트랜잭션 · 역할 애너테이션이 없다 — 뭉치 안의 외부 호출은 뭉치가 정한 트랜잭션 경계 그대로이고, 회차(두 역할 공통)와 처리 현황
 * 조회(화면 역할)가 함께 쓴다. 기록은 한 문장 쓰기라 따로 트랜잭션을 열지 않는다. 기록을 만들거나 쓰지 못하면 오류 로그를 남기고 삼킨다 —
 * 뭉치의 쓰기는 이미 커밋됐다. 뭉치가 예외를 던진 회차는 그 예외를 다시 던지고, 기록하지 못한 까닭은 그 예외에 덧붙인다.
 */
@Service
class RoundRunService implements RoundRunner, RoundActivity {

	private static final Logger logger = LoggerFactory.getLogger(RoundRunService.class);

	/** 기록을 남기지 못했을 때의 오류 — 회차 이름 · 끝난 까닭 · 뭉치 수 · 처리 수 */
	static final String RECORD_FAILED = "회차 기록을 남기지 못했다 — {} {}, 뭉치 {}개 · 처리 {}건";

	private final RoundRunRepository runs;

	private final AppClock clock;

	RoundRunService(RoundRunRepository runs, AppClock clock) {
		this.runs = Objects.requireNonNull(runs, "runs");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public void run(String round, Supplier<RoundBatch> batch) {
		RoundRules.requireName(round);
		Objects.requireNonNull(batch, "batch");
		Instant started = this.clock.instant();
		int batches = 0;
		int handled = 0;
		RoundEnd end;
		try {
			while (true) {
				RoundBatch done = Objects.requireNonNull(batch.get(), "batch result");
				batches++;
				handled = Math.addExact(handled, done.handled());
				Optional<RoundEnd> stop = RoundRules.endAfter(done.end(), this.clock.elapsedSince(started));
				if (stop.isPresent()) {
					end = stop.get();
					break;
				}
			}
		}
		catch (RuntimeException ex) {
			try {
				this.runs.insert(new RoundRun(round, started, this.clock.instant(), batches, handled, RoundEnd.FAILED));
			}
			catch (RuntimeException recordFailure) {
				ex.addSuppressed(recordFailure);
			}
			throw ex;
		}
		record(round, started, batches, handled, end);
	}

	@Override
	public RoundSummary recent(String round) {
		RoundRules.requireName(round);
		return this.runs.recent(round, this.clock.instant().minus(RoundRules.RECENT));
	}

	/** 기록을 만들거나 쓰지 못하면(쓰기 실패 · 시계가 뒤로 가 끝난 시각이 시작보다 앞섬) 오류 로그를 남기고 삼킨다 */
	private void record(String round, Instant started, int batches, int handled, RoundEnd end) {
		try {
			this.runs.insert(new RoundRun(round, started, this.clock.instant(), batches, handled, end));
		}
		catch (RuntimeException ex) {
			logger.error(RECORD_FAILED, round, end, batches, handled, ex);
		}
	}

}
