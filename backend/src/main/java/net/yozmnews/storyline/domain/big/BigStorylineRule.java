package net.yozmnews.storyline.domain.big;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Objects;

import net.yozmnews.event.EventTally;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.BigStorylineCriteria;

/**
 * 큰 판정 규칙의 정본 — docs/specs/storyline.md §4 · storyline-R19.
 * <p>
 * 보도 강도는 기간 집계의 중요도 합을 조회 기간에 기사가 있는 소스 수와 조회 기간의 길이(일)로 나눈 값이고, 보도 강도가 기준 이상이면 큰
 * 스토리라인이다(경계 포함). 식을 바꾸는 자리는 이 타입 하나다 — 큰 스토리라인 조회 · 미리보기 · 목록의 큰 여부가 모두 이 규칙을 부른다.
 */
public final class BigStorylineRule {

	/** 큰 스토리라인의 순서 — 보도 강도 내림차순, 스토리라인 ID 오름차순 */
	public static final Comparator<BigStoryline> ORDER = Comparator.comparing(BigStoryline::intensity)
		.reversed()
		.thenComparingLong(big -> big.storyline().id().value());

	/** 하루의 길이(초) — 조회 기간의 길이를 일수로 옮기는 정본 */
	private static final BigDecimal DAY = BigDecimal.valueOf(86_400);

	private BigStorylineRule() {
	}

	/** 보도 강도 — 소수 {@link BigStorylineCriteria#INTENSITY_SCALE} 자리로 반올림한 표시값. 소스 수가 0 이면 0 */
	public static BigDecimal intensity(EventTally tally, BigBasis basis) {
		Objects.requireNonNull(tally, "tally");
		Objects.requireNonNull(basis, "basis");
		if (basis.sources() == 0) {
			return BigDecimal.ZERO.setScale(BigStorylineCriteria.INTENSITY_SCALE);
		}
		return tally.importanceSum()
			.multiply(DAY)
			.divide(divisor(basis), BigStorylineCriteria.INTENSITY_SCALE, RoundingMode.HALF_UP);
	}

	/** 보도 강도가 기준에 이르는가 — 경계 포함. 나누지 않고 곱으로 견준다(표시값의 반올림으로 뒤집히지 않는다) */
	public static boolean isBig(EventTally tally, BigBasis basis, BigStorylineCriteria criteria) {
		Objects.requireNonNull(tally, "tally");
		Objects.requireNonNull(basis, "basis");
		Objects.requireNonNull(criteria, "criteria");
		return basis.sources() > 0
				&& tally.importanceSum().multiply(DAY).compareTo(criteria.minIntensity().multiply(divisor(basis))) >= 0;
	}

	/** 소스 수 × 조회 기간의 길이(초) — 초 아래도 버리지 않는다 */
	private static BigDecimal divisor(BigBasis basis) {
		BigDecimal seconds = BigDecimal.valueOf(basis.length().getSeconds())
			.add(BigDecimal.valueOf(basis.length().getNano(), 9));
		return BigDecimal.valueOf(basis.sources()).multiply(seconds);
	}

}
