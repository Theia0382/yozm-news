package net.yozmnews.report.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;

import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.domain.generator.TokenUsage;
import org.junit.jupiter.api.Test;

/**
 * 리포트 상태 전이 — 예약 · 시작 · 끝내기와 거부, 메시지 자르기, 끝낸 시각의 정밀도.
 * <p>
 * 지키는 규칙: report-R1
 */
class ReportTests {

	private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

	private static final ReportPeriod WEEK = new ReportPeriod(ReportPeriodKind.WEEK,
			Instant.parse("2026-11-16T00:00:00Z"), Instant.parse("2026-11-23T00:00:00Z"));

	@Test
	void schedulesWithThePeriodAndNothingElse() {
		Report report = Report.schedule(new UserId(7), WEEK, NOW);

		assertThat(report.status()).isEqualTo(ReportStatus.SCHEDULED);
		assertThat(report.userId()).isEqualTo(new UserId(7));
		assertThat(report.period()).isEqualTo(WEEK);
		assertThat(report.createdAt()).isEqualTo(NOW);
		assertThat(report.provider()).isEmpty();
		assertThat(report.startedAt()).isEmpty();
		assertThat(report.completedAt()).isEmpty();
	}

	@Test
	void aPeriodEndsAfterItStarts() {
		Instant at = Instant.parse("2026-11-16T00:00:00Z");

		assertThatIllegalArgumentException().isThrownBy(() -> new ReportPeriod(ReportPeriodKind.MONTH, at, at));
		assertThat(new ReportPeriod(ReportPeriodKind.MONTH, at, at.plusSeconds(1)).end()).isEqualTo(at.plusSeconds(1));
	}

	@Test
	void startsOnlyFromScheduledAndRecordsTheProviderKeyAndModel() {
		Report running = scheduled().start(ReportFixtures.MODEL, NOW.plusSeconds(1));

		assertThat(running.status()).isEqualTo(ReportStatus.RUNNING);
		assertThat(running.provider()).contains("openai");
		assertThat(running.model()).contains("gpt-5.5-mini");
		assertThat(running.startedAt()).contains(NOW.plusSeconds(1));
		assertThatIllegalStateException().isThrownBy(() -> running.start(ReportFixtures.MODEL, NOW))
			.withMessage("생성을 기다리는 리포트만 시작할 수 있다: RUNNING");
	}

	@Test
	void completesOnlyFromRunning() {
		assertThatIllegalStateException().isThrownBy(() -> scheduled().complete(new Completion.Empty(), NOW))
			.withMessage("생성 중이 아닌 리포트는 끝낼 수 없다: SCHEDULED");
		Report empty = running().complete(new Completion.Empty(), NOW);
		assertThatIllegalStateException().isThrownBy(() -> empty.complete(new Completion.Empty(), NOW))
			.withMessage("생성 중이 아닌 리포트는 끝낼 수 없다: EMPTY");
		assertThatIllegalStateException().isThrownBy(() -> empty.start(ReportFixtures.MODEL, NOW))
			.withMessage("생성을 기다리는 리포트만 시작할 수 있다: EMPTY");
	}

	@Test
	void readyRecordsTokensAndDurationAndKeepsTheModel() {
		Report ready = running().complete(new Completion.Ready(new TokenUsage(120, 45), Duration.ofMillis(1_234)),
				NOW.plusSeconds(5));

		assertThat(ready.status()).isEqualTo(ReportStatus.READY);
		assertThat(ready.usage()).contains(new TokenUsage(120, 45));
		assertThat(ready.took()).contains(Duration.ofMillis(1_234));
		assertThat(ready.model()).contains("gpt-5.5-mini");
		assertThat(ready.failure()).isEmpty();
		assertThat(ready.completedAt()).contains(NOW.plusSeconds(5));
	}

	@Test
	void emptyHasNoResultFields() {
		Report empty = running().complete(new Completion.Empty(), NOW);

		assertThat(empty.status()).isEqualTo(ReportStatus.EMPTY);
		assertThat(empty.usage()).isEmpty();
		assertThat(empty.took()).isEmpty();
		assertThat(empty.failure()).isEmpty();
	}

	@Test
	void failedRecordsTheCodeAndCutsTheMessageAtFiveHundredCodePoints() {
		String fiveHundred = "가".repeat(500);
		Report kept = running().complete(new Completion.Failed(ReportFailure.KEY_MISSING, fiveHundred, null, null), NOW);
		Report cut = running().complete(
				new Completion.Failed(ReportFailure.UNREADABLE_OUTPUT, fiveHundred + "나", new TokenUsage(1, 2),
						Duration.ofMillis(3)),
				NOW);

		assertThat(kept.status()).isEqualTo(ReportStatus.FAILED);
		assertThat(kept.failure()).contains(ReportFailure.KEY_MISSING);
		assertThat(kept.errorMessage()).contains(fiveHundred);
		assertThat(kept.usage()).isEmpty();
		assertThat(kept.took()).isEmpty();
		assertThat(cut.errorMessage()).contains(fiveHundred);
		assertThat(cut.usage()).contains(new TokenUsage(1, 2));
		assertThat(cut.took()).contains(Duration.ofMillis(3));
	}

	@Test
	void truncatesTheCompletionTimeToMicroseconds() {
		Report ready = running().complete(new Completion.Empty(), Instant.parse("2026-11-23T00:00:00.123456789Z"));

		assertThat(ready.completedAt()).contains(Instant.parse("2026-11-23T00:00:00.123456Z"));
	}

	@Test
	void aFailureWithTokensHasADuration() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Completion.Failed(ReportFailure.OUTPUT_TRUNCATED, "잘림", new TokenUsage(1, 1), null))
			.withMessage("사용량이 있으면 소요 시간도 있다");
	}

	private static Report scheduled() {
		return Report.schedule(new UserId(7), WEEK, NOW);
	}

	private static Report running() {
		return scheduled().start(ReportFixtures.MODEL, NOW);
	}

}
