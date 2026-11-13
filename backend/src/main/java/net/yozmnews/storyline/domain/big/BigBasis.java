package net.yozmnews.storyline.domain.big;

import java.time.Duration;
import java.util.Objects;

import net.yozmnews.event.EventPeriod;
import net.yozmnews.event.EventTally;

/**
 * 큰 판정의 분모 — 조회 기간에 기사가 있는 서로 다른 소스 수와 조회 기간의 길이 · docs/specs/storyline.md §4 · storyline-R19.
 *
 * @param sources 기간 전체 집계의 소스 수 — 0 이상
 * @param length 조회 기간의 길이 — 양수
 */
public record BigBasis(int sources, Duration length) {

	public BigBasis {
		Objects.requireNonNull(length, "length");
		if (sources < 0 || length.isZero() || length.isNegative()) {
			throw new IllegalArgumentException("큰 판정의 분모가 범위 밖이다: 소스 " + sources + ", 길이 " + length);
		}
	}

	/** 기간 전체 집계와 조회 기간에서 만든다 */
	public static BigBasis of(EventTally total, EventPeriod period) {
		Objects.requireNonNull(total, "total");
		Objects.requireNonNull(period, "period");
		return new BigBasis(total.sources(), Duration.between(period.from(), period.to()));
	}

}
