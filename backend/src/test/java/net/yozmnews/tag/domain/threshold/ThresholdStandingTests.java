package net.yozmnews.tag.domain.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import net.yozmnews.platform.ChangeActor;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.ThresholdChangeView;
import net.yozmnews.tag.ThresholdCondition;
import org.junit.jupiter.api.Test;

/**
 * 어긋난 상태 — 지금 값 리터럴과 리터럴 이력 · 덮음으로 만든 저장 바닥으로 상태 집합을 본다. 바닥은 지금 값과 따로 만든다 — 바닥의
 * 마지막 저장 기준이 지금 저장 기준과 달라도 판정은 두 입력을 따로 본다.
 * <p>
 * 지키는 규칙: tag-R36
 */
class ThresholdStandingTests {

	private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

	private static final Instant T1 = Instant.parse("2026-10-06T00:00:00Z");

	@Test
	void aConsistentStateHasNoCondition() {
		assertThat(of("0.5", "0.1", uniform("0.1"))).isEmpty();
	}

	@Test
	void aTagThresholdBelowTheStoreThresholdIsAStandingCondition() {
		assertThat(of("0.05", "0.1", uniform("0.1"))).containsExactly(ThresholdCondition.TAG_BELOW_STORE);
		// 경계: 같은 값은 낮은 것이 아니다
		assertThat(of("0.1", "0.1", uniform("0.1"))).isEmpty();
	}

	@Test
	void aTagThresholdBelowAHigherPastStoreThresholdIsAStandingCondition() {
		assertThat(of("0.15", "0.1", uniform("0.2"))).containsExactly(ThresholdCondition.TAG_BELOW_PAST_STORE);
		// 경계: 지난 최댓값과 같은 태그 기준은 낮은 것이 아니다
		assertThat(of("0.2", "0.1", uniform("0.2"))).isEmpty();
	}

	@Test
	void aPastMaximumNoHigherThanTheCurrentStoreThresholdIsLeftToTagBelowStore() {
		// 지난 최댓값이 지금 저장 기준과 같으면 TAG_BELOW_STORE 만 — 같은 까닭을 두 번 알리지 않는다
		assertThat(of("0.05", "0.2", uniform("0.2"))).containsExactly(ThresholdCondition.TAG_BELOW_STORE);
		// 대조: 지난 최댓값이 더 높으면 둘 다다
		assertThat(of("0.05", "0.1", uniform("0.2"))).containsExactlyInAnyOrder(ThresholdCondition.TAG_BELOW_STORE,
				ThresholdCondition.TAG_BELOW_PAST_STORE);
	}

	@Test
	void mixedStoreFloorsOfTheWholeVersionAreAStandingCondition() {
		// 0.2 → 0.1 로 내렸고 덮음이 없다 — 지난 최댓값 0.2 는 태그 기준 0.5 아래라 그 상태는 없다
		assertThat(of("0.5", "0.1", lowered(List.of()))).containsExactly(ThresholdCondition.STORE_THRESHOLD_CHANGED);
	}

	@Test
	void aCoverageThatMakesTheWholeVersionUniformLiftsTheMixedCondition() {
		// 대조 — 처음 값 시각부터 내린 시각 + 31분까지 0.1 로 덮으면 바닥은 0.1 하나다
		assertThat(of("0.5", "0.1",
				lowered(List.of(new StoreCoverage(T0, T1.plus(Duration.ofMinutes(31)), new BigDecimal("0.1"))))))
			.isEmpty();
	}

	private static Set<ThresholdCondition> of(String tag, String store, StoreFloors floors) {
		return ThresholdStanding.of(new ThresholdValues(new BigDecimal(tag), new BigDecimal(store)), floors);
	}

	/** 처음 값 하나뿐인 바닥 — 지난 최댓값이 그 값이고 섞이지 않았다 */
	private static StoreFloors uniform(String store) {
		return StoreFloors.of(MultiTagKind.TOPIC, List.of(initial(store)), List.of(), Duration.ofMinutes(30));
	}

	/** 처음 값 0.2(T0) → 0.1(T1) 로 내린 바닥 */
	private static StoreFloors lowered(List<StoreCoverage> coverages) {
		return StoreFloors.of(MultiTagKind.TOPIC, List.of(new ThresholdChangeView(MultiTagKind.TOPIC,
				new BigDecimal("0.5"), new BigDecimal("0.2"), new BigDecimal("0.5"), new BigDecimal("0.1"),
				ChangeActor.OPERATOR, T1), initial("0.2")), coverages, Duration.ofMinutes(30));
	}

	private static ThresholdChangeView initial(String store) {
		return new ThresholdChangeView(MultiTagKind.TOPIC, null, null, new BigDecimal("0.5"), new BigDecimal(store),
				ChangeActor.SYSTEM, T0);
	}

}
