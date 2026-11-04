package net.yozmnews.evaluation.domain.threshold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.ThresholdChangeView;
import org.junit.jupiter.api.Test;

/**
 * 저장 바닥 계산 — 리터럴 이력 · 덮음으로 지난 저장 기준 최댓값과 바닥이 섞였는지를 본다. 이력은 저장소 순서(최근 것부터)로 넘기고, 읽기 지연은
 * 프로덕션 값과 같은 30분을 리터럴로 쓴다.
 * <p>
 * 지키는 규칙: evaluation-R12 · evaluation-R29
 */
class StoreFloorsTests {

	private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

	private static final Instant T1 = Instant.parse("2026-10-06T00:00:00Z");

	private static final Duration LAG = Duration.ofMinutes(30);

	@Test
	void withoutCoveragesThePastMaximumIsTheLargestStoreThresholdOfTheHistory() {
		StoreFloors floors = floors(List.of(change("0.3", "0.2", T1.plus(Duration.ofDays(1))), change("0.1", "0.3", T1),
				initial("0.1", T0)));

		assertThat(floors.pastMax()).isEqualByComparingTo("0.3");
	}

	@Test
	void twoChangesAtTheSameInstantBothCount() {
		StoreFloors floors = floors(List.of(change("0.4", "0.2", T1), change("0.1", "0.4", T1), initial("0.1", T0)));

		assertThat(floors.pastMax()).isEqualByComparingTo("0.4");
	}

	@Test
	void aChangeRecordedWithAnEarlierClockStillCounts() {
		// 다음 행의 시각이 앞선 이력(프로세스마다 다른 시계) — 0.4 는 그 행의 순간 하나만 유효해도 최댓값에 든다
		StoreFloors floors = floors(List.of(change("0.4", "0.2", T0), change("0.1", "0.4", T1), initial("0.1", T0)));

		assertThat(floors.pastMax()).isEqualByComparingTo("0.4");
	}

	@Test
	void coveringTheWholeVersionBringsThePastMaximumDownToTheCoveredThreshold() {
		StoreFloors floors = loweredAtT1(new StoreCoverage(T0, T1.plus(Duration.ofHours(1)), new BigDecimal("0.1")));

		assertThat(floors.pastMax()).isEqualByComparingTo("0.1");
	}

	@Test
	void anUncoveredStretchKeepsItsStoreThreshold() {
		StoreFloors floors = loweredAtT1(
				new StoreCoverage(T0.plus(Duration.ofHours(12)), T1.plus(Duration.ofHours(1)), new BigDecimal("0.1")));

		assertThat(floors.pastMax()).isEqualByComparingTo("0.3");
	}

	@Test
	void evaluationsWithinTheReadLagAfterAChangeMayHaveUsedTheOldThreshold() {
		// 내린 시각 + 29분까지만 덮으면 + 29분 ~ + 30분의 평가는 옛 기준(0.3)으로 저장됐을 수 있다
		assertThat(loweredAtT1(new StoreCoverage(T0, T1.plus(Duration.ofMinutes(29)), new BigDecimal("0.1"))).pastMax())
			.isEqualByComparingTo("0.3");
		// + 31분까지 덮으면 그 뒤 평가는 새 기준(0.1)으로 읽었다
		assertThat(loweredAtT1(new StoreCoverage(T0, T1.plus(Duration.ofMinutes(31)), new BigDecimal("0.1"))).pastMax())
			.isEqualByComparingTo("0.1");
	}

	@Test
	void aCoverageAtAHigherThresholdLowersOnlyToThatThreshold() {
		StoreFloors floors = loweredAtT1(new StoreCoverage(T0, T1.plus(Duration.ofHours(1)), new BigDecimal("0.2")));

		assertThat(floors.pastMax()).isEqualByComparingTo("0.2");
	}

	@Test
	void theFloorIsMixedOnlyWhereTheStoreThresholdChangesWithinThePeriod() {
		StoreFloors floors = floors(List.of(change("0.1", "0.3", T1), initial("0.1", T0)));

		assertThat(floors.mixed(T0.plus(Duration.ofDays(1)), T0.plus(Duration.ofDays(2)))).isFalse();
		assertThat(floors.mixed(T1.minus(Duration.ofDays(1)), T1.plus(Duration.ofDays(1)))).isTrue();
		assertThat(floors.mixed(T1.plus(Duration.ofMinutes(31)), T1.plus(Duration.ofDays(1)))).isFalse();
	}

	@Test
	void aCoverageOfThePeriodMakesItsFloorUniform() {
		StoreFloors floors = loweredAtT1(
				new StoreCoverage(T1.minus(Duration.ofDays(1)), T1.plus(Duration.ofDays(1)), new BigDecimal("0.1")));

		assertThat(floors.mixed(T1.minus(Duration.ofDays(1)), T1.plus(Duration.ofDays(1)))).isFalse();
		// 대조 — 덮지 않은 버전은 그 기간에 섞였다
		assertThat(loweredAtT1().mixed(T1.minus(Duration.ofDays(1)), T1.plus(Duration.ofDays(1)))).isTrue();
	}

	@Test
	void timesBeforeTheInitialValueAreNotLookedAt() {
		StoreFloors floors = floors(List.of(change("0.1", "0.3", T0.plus(Duration.ofHours(1))), initial("0.1", T0)));

		assertThat(floors.mixed(T0.minus(Duration.ofDays(2)), T0.minus(Duration.ofDays(1)))).isFalse();
		assertThat(floors.mixed(T0.minus(Duration.ofDays(1)), T0.plus(Duration.ofMinutes(30)))).isFalse();
		assertThat(floors.mixed(T0.minus(Duration.ofDays(1)), T0.plus(Duration.ofHours(2)))).isTrue();
	}

	@Test
	void theSameMagnitudeIsTheSameStoreThreshold() {
		StoreFloors floors = floors(List.of(change("0.1", "0.10", T1), initial("0.1", T0)));

		assertThat(floors.mixed(T0, T1.plus(Duration.ofDays(1)))).isFalse();
	}

	@Test
	void rowsOfTheOtherTagKindAreIgnored() {
		StoreFloors floors = StoreFloors.of(MultiTagKind.TOPIC,
				List.of(new ThresholdChangeView(MultiTagKind.REGION, new BigDecimal("0.5"), new BigDecimal("0.1"),
						new BigDecimal("0.5"), new BigDecimal("0.9"), ChangeActor.OPERATOR, T1), initial("0.1", T0)),
				List.of(), LAG);

		assertThat(floors.pastMax()).isEqualByComparingTo("0.1");
	}

	@Test
	void aHistoryWithoutItsInitialRowIsABrokenInvariant() {
		assertThatIllegalStateException().isThrownBy(() -> floors(List.of(change("0.1", "0.3", T1))));
		assertThatIllegalStateException().isThrownBy(() -> floors(List.of()));
	}

	@Test
	void theProductionReadLagIsTheEvaluationLease() {
		// 세 인자 of 는 읽기 지연 30분 — 내린 시각 + 29분까지 덮으면 옛 기준이 남고 + 31분이면 남지 않는다
		List<ThresholdChangeView> changes = List.of(change("0.3", "0.1", T1), initial("0.3", T0));
		assertThat(StoreFloors.of(MultiTagKind.TOPIC, changes,
				List.of(new StoreCoverage(T0, T1.plus(Duration.ofMinutes(29)), new BigDecimal("0.1"))))
			.pastMax()).isEqualByComparingTo("0.3");
		assertThat(StoreFloors.of(MultiTagKind.TOPIC, changes,
				List.of(new StoreCoverage(T0, T1.plus(Duration.ofMinutes(31)), new BigDecimal("0.1"))))
			.pastMax()).isEqualByComparingTo("0.1");
	}

	/** 처음 값 0.3(T0) → 0.1(T1) 로 내린 주제 이력 */
	private static StoreFloors loweredAtT1(StoreCoverage... coverages) {
		return StoreFloors.of(MultiTagKind.TOPIC, List.of(change("0.3", "0.1", T1), initial("0.3", T0)),
				List.of(coverages), LAG);
	}

	private static StoreFloors floors(List<ThresholdChangeView> changes) {
		return StoreFloors.of(MultiTagKind.TOPIC, changes, List.of(), LAG);
	}

	private static ThresholdChangeView initial(String store, Instant at) {
		return new ThresholdChangeView(MultiTagKind.TOPIC, null, null, new BigDecimal("0.5"), new BigDecimal(store),
				ChangeActor.SYSTEM, at);
	}

	private static ThresholdChangeView change(String previousStore, String store, Instant at) {
		return new ThresholdChangeView(MultiTagKind.TOPIC, new BigDecimal("0.5"), new BigDecimal(previousStore),
				new BigDecimal("0.5"), new BigDecimal(store), ChangeActor.OPERATOR, at);
	}

}
