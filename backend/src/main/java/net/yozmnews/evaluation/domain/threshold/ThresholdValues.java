package net.yozmnews.evaluation.domain.threshold;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagThresholds;

/**
 * 태그 기준 · 저장 기준 한 쌍 — docs/specs/evaluation.md §4 · evaluation-R10.
 * <p>
 * 값은 0 이상 1 이하, 소수 셋째 자리까지다(끝의 0 은 세지 않는다). 소수 셋째 자리로 맞춰 가지므로 같은 크기면 같은 값이다.
 */
public record ThresholdValues(BigDecimal tagThreshold, BigDecimal storeThreshold) {

	/** 소수 자리 상한 */
	static final int SCALE = 3;

	/**
	 * @throws IllegalArgumentException 두 값 가운데 하나라도 범위 밖일 때 — 범위를 모르는 입력은 {@link #of} 로 만든다
	 */
	public ThresholdValues {
		Objects.requireNonNull(tagThreshold, "tagThreshold");
		Objects.requireNonNull(storeThreshold, "storeThreshold");
		if (!valid(tagThreshold) || !valid(storeThreshold)) {
			throw new IllegalArgumentException("기준은 0 ~ 1, 소수 셋째 자리까지다: " + tagThreshold + " / " + storeThreshold);
		}
		tagThreshold = tagThreshold.setScale(SCALE);
		storeThreshold = storeThreshold.setScale(SCALE);
	}

	/**
	 * 두 값으로 한 쌍을 만든다.
	 * @return 하나라도 범위 밖이면 빈 값
	 */
	public static Optional<ThresholdValues> of(BigDecimal tagThreshold, BigDecimal storeThreshold) {
		if (!valid(tagThreshold) || !valid(storeThreshold)) {
			return Optional.empty();
		}
		return Optional.of(new ThresholdValues(tagThreshold, storeThreshold));
	}

	/** 이 값을 그 태그 종류의 공개 값으로 옮긴다 */
	public TagThresholds toView(MultiTagKind kind) {
		return new TagThresholds(kind, this.tagThreshold, this.storeThreshold);
	}

	/** 값 하나가 범위 안인가 — 0 이상 1 이하, 끝의 0 을 뺀 소수 자리가 셋 이하 */
	public static boolean valid(BigDecimal value) {
		Objects.requireNonNull(value, "value");
		return value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0 && value.stripTrailingZeros().scale() <= SCALE;
	}

}
