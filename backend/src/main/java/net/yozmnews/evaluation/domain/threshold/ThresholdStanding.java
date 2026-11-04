package net.yozmnews.evaluation.domain.threshold;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.evaluation.ThresholdCondition;

/**
 * 어긋난 상태 판정과 저장 기준이 바뀐 적이 있는가의 정본 — docs/specs/evaluation.md §4 · evaluation-R29 · evaluation-R34.
 * <p>
 * 경고({@link ThresholdRule})는 이번 변경이 어긋남을 만들 때 한 번 나오고, 어긋난 상태는 지금 값과 이력으로 정해져 남아 있는 동안 나온다.
 */
public final class ThresholdStanding {

	private ThresholdStanding() {
	}

	/**
	 * 지금 값의 어긋난 상태를 정한다.
	 * @param pastMaxStoreThreshold 그 버전 · 그 태그 종류의 이력(처음 값 포함)에 있는 저장 기준의 최댓값
	 * @param storeChanged 그 버전 · 그 태그 종류의 저장 기준이 바뀐 적이 있는가 — {@link #storeChanged}
	 */
	public static Set<ThresholdCondition> of(ThresholdValues current, BigDecimal pastMaxStoreThreshold,
			boolean storeChanged) {
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(pastMaxStoreThreshold, "pastMaxStoreThreshold");
		Set<ThresholdCondition> conditions = EnumSet.noneOf(ThresholdCondition.class);
		if (lower(current.tagThreshold(), current.storeThreshold())) {
			conditions.add(ThresholdCondition.TAG_BELOW_STORE);
		}
		// 지금 저장 기준 때문이면 TAG_BELOW_STORE 가 말한다 — 지난 최댓값이 지금보다 높을 때만 따로 알린다
		if (lower(current.tagThreshold(), pastMaxStoreThreshold)
				&& lower(current.storeThreshold(), pastMaxStoreThreshold)) {
			conditions.add(ThresholdCondition.TAG_BELOW_PAST_STORE);
		}
		if (storeChanged) {
			conditions.add(ThresholdCondition.STORE_THRESHOLD_CHANGED);
		}
		return conditions;
	}

	/** 이력에 그 태그 종류의 저장 기준이 바뀐 행이 있는가 — 처음 값 행(이전 값 없음)은 바뀐 것이 아니다 */
	public static boolean storeChanged(List<ThresholdChangeView> changes, MultiTagKind kind) {
		return changes.stream()
			.filter(change -> change.kind() == kind)
			.anyMatch(change -> change.previousStoreThreshold() != null
					&& change.previousStoreThreshold().compareTo(change.storeThreshold()) != 0);
	}

	private static boolean lower(BigDecimal value, BigDecimal than) {
		return value.compareTo(than) < 0;
	}

}
