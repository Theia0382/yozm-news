package net.yozmnews.evaluation.domain.threshold;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.evaluation.ThresholdCondition;

/**
 * 어긋난 상태 판정의 정본 — 입력은 지금 값과 저장 바닥(evaluation-R32) · docs/specs/evaluation.md §4 · evaluation-R34.
 * <p>
 * 경고({@link ThresholdRule})는 이번 변경이 어긋남을 만들 때 한 번 나오고, 어긋난 상태는 지금 값과 저장 바닥으로 정해져 남아 있는 동안
 * 나온다.
 */
public final class ThresholdStanding {

	private ThresholdStanding() {
	}

	/**
	 * 지금 값의 어긋난 상태를 정한다.
	 * @param floors 그 버전 · 그 태그 종류의 저장 바닥 — 지난 저장 기준 최댓값({@link StoreFloors#pastMax()})과 버전 전체의 바닥 섞임
	 * ({@link StoreFloors#mixed()})을 낸다
	 */
	public static Set<ThresholdCondition> of(ThresholdValues current, StoreFloors floors) {
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(floors, "floors");
		BigDecimal pastMax = floors.pastMax();
		Set<ThresholdCondition> conditions = EnumSet.noneOf(ThresholdCondition.class);
		if (lower(current.tagThreshold(), current.storeThreshold())) {
			conditions.add(ThresholdCondition.TAG_BELOW_STORE);
		}
		// 지금 저장 기준 때문이면 TAG_BELOW_STORE 가 말한다 — 지난 최댓값이 지금보다 높을 때만 따로 알린다
		if (lower(current.tagThreshold(), pastMax) && lower(current.storeThreshold(), pastMax)) {
			conditions.add(ThresholdCondition.TAG_BELOW_PAST_STORE);
		}
		if (floors.mixed()) {
			conditions.add(ThresholdCondition.STORE_THRESHOLD_CHANGED);
		}
		return conditions;
	}

	private static boolean lower(BigDecimal value, BigDecimal than) {
		return value.compareTo(than) < 0;
	}

}
