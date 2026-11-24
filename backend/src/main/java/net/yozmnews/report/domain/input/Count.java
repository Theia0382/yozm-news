package net.yozmnews.report.domain.input;

import net.yozmnews.report.domain.material.CoverageTrend;
import org.jspecify.annotations.Nullable;

/**
 * 보도량 하나 — 이번 기간 수와 이은 직전 값 · docs/specs/report.md §5 · report-R15 · report-R18
 * <p>
 * 판정은 따로 갖지 않고 {@link CoverageTrend#of} 로 낸다 — 수와 판정이 어긋날 수 없다.
 *
 * @param current 이번 기간 기사 수
 * @param previous 직전 리포트의 스냅숏에서 이은 기사 수 — 없으면 null
 */
public record Count(long current, @Nullable Long previous) {

	/**
	 * @throws IllegalArgumentException 수가 0 보다 작을 때
	 */
	public Count {
		if (current < 0 || (previous != null && previous < 0)) {
			throw new IllegalArgumentException("기사 수는 0 이상이다: " + current + " · " + previous);
		}
	}

	/** 보도량 판정(report-R15) */
	public CoverageTrend.Trend trend() {
		return CoverageTrend.of(this.current, this.previous);
	}

}
