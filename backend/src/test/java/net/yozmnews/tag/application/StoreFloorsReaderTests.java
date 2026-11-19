package net.yozmnews.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import net.yozmnews.TestClocks;
import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.ThresholdChangeView;
import net.yozmnews.tag.domain.backfill.TagBackfillRepository;
import net.yozmnews.tag.domain.threshold.StoreCoverage;
import org.junit.jupiter.api.Test;

/**
 * 저장 바닥 결선의 읽기 지연 — 결선이 받은 시계의 경과로 읽기 지연(평가 회차의 점유 기간 30분)을 잰다. 저장 기준을 T1 에 0.3 에서 0.1 로
 * 내렸고, 끝난 채움이 T1 + 24시간 15분까지 0.1 로 덮었다. 기대값은 손으로 셈한 리터럴이다.
 */
class StoreFloorsReaderTests {

	private static final TagSetId VERSION = new TagSetId(3);

	private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

	private static final Instant T1 = Instant.parse("2026-10-06T00:00:00Z");

	private static final List<ThresholdChangeView> LOWERED_AT_T1 = List.of(
			new ThresholdChangeView(MultiTagKind.TOPIC, new BigDecimal("0.5"), new BigDecimal("0.3"),
					new BigDecimal("0.5"), new BigDecimal("0.1"), ChangeActor.OPERATOR, T1),
			new ThresholdChangeView(MultiTagKind.TOPIC, null, null, new BigDecimal("0.5"), new BigDecimal("0.3"),
					ChangeActor.SYSTEM, T0));

	@Test
	void aJumpOfTheClockAfterTheChangeKeepsTheOldThresholdWithinTheReadLag() {
		// 내린 뒤 10분에 24시간을 넘겼다 — 덮음이 끝난 T1 + 24시간 15분은 내린 지 경과 15분이라 그 뒤 평가는 옛 기준으로 읽었을 수 있다
		AppClock jumped = TestClocks.movable(T1.plus(Duration.ofMinutes(10))).jump(Duration.ofHours(24));

		assertThat(reader(jumped).floors(VERSION, MultiTagKind.TOPIC, LOWERED_AT_T1).pastMax())
			.isEqualByComparingTo("0.3");
	}

	@Test
	void withoutJumpsTheReadLagIsThePlainLease() {
		// 대조 — 넘긴 기록이 없으면 내린 지 30분 뒤부터 새 기준이라, T1 + 24시간 15분까지 덮으면 옛 기준이 남지 않는다
		AppClock real = TestClocks.fixed(T1.plus(Duration.ofHours(25)));

		assertThat(reader(real).floors(VERSION, MultiTagKind.TOPIC, LOWERED_AT_T1).pastMax())
			.isEqualByComparingTo("0.1");
	}

	private static StoreFloorsReader reader(AppClock clock) {
		TagBackfillRepository backfills = mock(TagBackfillRepository.class);
		when(backfills.coverages(VERSION, MultiTagKind.TOPIC)).thenReturn(
				List.of(new StoreCoverage(T0, T1.plus(Duration.ofHours(24)).plus(Duration.ofMinutes(15)),
						new BigDecimal("0.1"))));
		return new StoreFloorsReader(backfills, clock);
	}

}
