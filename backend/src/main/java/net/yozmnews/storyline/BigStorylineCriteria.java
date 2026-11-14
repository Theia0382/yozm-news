package net.yozmnews.storyline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * 큰 판정 기준 — 보도 강도의 하한 · docs/specs/storyline.md §3 · storyline-R19 · storyline-R20.
 * <p>
 * 소수 둘째 자리까지이고 둘째 자리로 맞춰 둔다 — 같은 값이면 같다({@code equals}).
 *
 * @param minIntensity 보도 강도가 이 값 이상이어야 한다 — 0 보다 크고 {@link #MAX_INTENSITY} 이하, 소수 둘째 자리까지
 */
public record BigStorylineCriteria(BigDecimal minIntensity) {

	/** 보도 강도 하한의 소수 자릿수 — 정본 */
	public static final int INTENSITY_SCALE = 2;

	/** 보도 강도 하한의 상한 — 정본 */
	public static final BigDecimal MAX_INTENSITY = new BigDecimal("9999.99");

	/** 처음 값 — 정본. 측정 전 목표 */
	public static final BigStorylineCriteria DEFAULT = new BigStorylineCriteria(new BigDecimal("15.00"));

	/**
	 * @throws IllegalArgumentException 하한이 0 이하이거나 {@link #MAX_INTENSITY} 를 넘거나 소수 둘째 자리를 넘을 때
	 */
	public BigStorylineCriteria {
		Objects.requireNonNull(minIntensity, "minIntensity");
		if (!valid(minIntensity)) {
			throw new IllegalArgumentException("큰 판정 기준이 범위 밖이다: 보도 강도 " + minIntensity.toPlainString());
		}
		minIntensity = minIntensity.setScale(INTENSITY_SCALE, RoundingMode.UNNECESSARY);
	}

	/**
	 * 범위를 판정해 만든다.
	 * @return 범위 밖이면 빈 값
	 */
	public static Optional<BigStorylineCriteria> of(BigDecimal minIntensity) {
		Objects.requireNonNull(minIntensity, "minIntensity");
		return valid(minIntensity) ? Optional.of(new BigStorylineCriteria(minIntensity)) : Optional.empty();
	}

	private static boolean valid(BigDecimal minIntensity) {
		return minIntensity.signum() > 0 && minIntensity.compareTo(MAX_INTENSITY) <= 0
				&& minIntensity.stripTrailingZeros().scale() <= INTENSITY_SCALE;
	}

}
