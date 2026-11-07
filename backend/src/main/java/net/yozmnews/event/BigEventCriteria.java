package net.yozmnews.event;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.evaluation.ImportanceScale;

/**
 * 큰 사건 기준 — 기간 안 서로 다른 소스 수의 하한과 중요도 평균의 하한 · docs/specs/event.md §3 · event-R15 · event-R21.
 * <p>
 * 중요도는 소수 둘째 자리까지이고 둘째 자리로 맞춰 둔다 — 같은 값이면 같다({@code equals}).
 *
 * @param minSources 서로 다른 소스 수가 이 값 이상이어야 한다 — 1 이상
 * @param minImportance 중요도 평균이 이 값 이상이어야 한다 — 중요도 수준의 범위 안, 소수 둘째 자리까지
 */
public record BigEventCriteria(int minSources, BigDecimal minImportance) {

	/** 중요도 하한의 소수 자릿수 — 정본 */
	public static final int IMPORTANCE_SCALE = 2;

	/** 처음 값 — 정본. 측정 전 목표 */
	public static final BigEventCriteria DEFAULT = new BigEventCriteria(3, new BigDecimal("3.00"));

	/**
	 * @throws IllegalArgumentException 소스 하한이 1 보다 작거나, 중요도 하한이 중요도 수준의 범위 밖이거나 소수 둘째 자리를 넘을 때
	 */
	public BigEventCriteria {
		Objects.requireNonNull(minImportance, "minImportance");
		if (!valid(minSources, minImportance)) {
			throw new IllegalArgumentException(
					"큰 사건 기준이 범위 밖이다: 소스 " + minSources + ", 중요도 " + minImportance.toPlainString());
		}
		minImportance = minImportance.setScale(IMPORTANCE_SCALE, RoundingMode.UNNECESSARY);
	}

	/**
	 * 범위를 판정해 만든다.
	 * @return 범위 밖이면 빈 값
	 */
	public static Optional<BigEventCriteria> of(int minSources, BigDecimal minImportance) {
		Objects.requireNonNull(minImportance, "minImportance");
		return valid(minSources, minImportance) ? Optional.of(new BigEventCriteria(minSources, minImportance))
				: Optional.empty();
	}

	private static boolean valid(int minSources, BigDecimal minImportance) {
		return minSources >= 1 && minImportance.compareTo(BigDecimal.valueOf(ImportanceScale.MIN_LEVEL)) >= 0
				&& minImportance.compareTo(BigDecimal.valueOf(ImportanceScale.MAX_LEVEL)) <= 0
				&& minImportance.stripTrailingZeros().scale() <= IMPORTANCE_SCALE;
	}

}
