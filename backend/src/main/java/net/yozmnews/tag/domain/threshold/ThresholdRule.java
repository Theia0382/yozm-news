package net.yozmnews.tag.domain.threshold;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.tag.ThresholdWarning;

/**
 * 태그 기준 · 저장 기준 변경 판정과 경고의 정본 — docs/specs/tag.md §4 · tag-R11 · tag-R12.
 * <p>
 * 앱은 태그 기준 · 저장 기준의 관계를 막지 않는다. 이번 변경이 이전 기사와 어긋남을 만들 때만 경고를 싣고, 이미 어긋난 상태에서 다른 값만 바꾸는 변경에는
 * 다시 알리지 않는다.
 */
public final class ThresholdRule {

	private ThresholdRule() {
	}

	/**
	 * 변경을 판정한다.
	 * @param current 지금 태그 기준 · 저장 기준
	 * @param pastMaxStoreThreshold 이 변경 전까지 그 버전 · 그 태그 종류의 지난 저장 기준 최댓값 — 이력과 끝난 채움의 저장 바닥 최댓값
	 * ({@link StoreFloors#pastMax()})
	 * @param tagThreshold 요청한 태그 기준
	 * @param storeThreshold 요청한 저장 기준
	 */
	public static ThresholdDecision decide(ThresholdValues current, BigDecimal pastMaxStoreThreshold,
			BigDecimal tagThreshold, BigDecimal storeThreshold) {
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(pastMaxStoreThreshold, "pastMaxStoreThreshold");
		Optional<ThresholdValues> requested = ThresholdValues.of(tagThreshold, storeThreshold);
		if (requested.isEmpty()) {
			return new ThresholdDecision.OutOfRange();
		}
		ThresholdValues next = requested.get();
		if (next.equals(current)) {
			return new ThresholdDecision.Unchanged();
		}
		Set<ThresholdWarning> warnings = EnumSet.noneOf(ThresholdWarning.class);
		if (lower(next.storeThreshold(), current.storeThreshold())) {
			warnings.add(ThresholdWarning.STORE_THRESHOLD_LOWERED);
		}
		if (lower(next.tagThreshold(), current.tagThreshold()) && lower(next.tagThreshold(), pastMaxStoreThreshold)) {
			warnings.add(ThresholdWarning.TAG_BELOW_PAST_STORE);
		}
		if (lower(next.tagThreshold(), next.storeThreshold())
				&& !lower(current.tagThreshold(), current.storeThreshold())) {
			warnings.add(ThresholdWarning.TAG_BELOW_STORE);
		}
		return new ThresholdDecision.Change(next, warnings);
	}

	private static boolean lower(BigDecimal value, BigDecimal than) {
		return value.compareTo(than) < 0;
	}

}
