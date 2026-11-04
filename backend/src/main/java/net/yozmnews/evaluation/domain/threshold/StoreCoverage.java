package net.yozmnews.evaluation.domain.threshold;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 끝난 채움이 덮은 평가 시각 범위 {@code [from, to)} 와 채운 저장 기준 — 한 버전 · 한 태그 종류 · docs/specs/evaluation.md §4.
 * <p>
 * 저장 바닥 계산({@link StoreFloors})의 입력이다. 건너뛴 평가가 있는 버전의 채움은 덮음이 아니다 — 부르는 쪽이 거른다.
 *
 * @param storeThreshold 채울 때 쓴 저장 기준 — 범위 안 평가에는 이 값 이상인 확률이 모두 저장돼 있다
 */
public record StoreCoverage(Instant from, Instant to, BigDecimal storeThreshold) {

	/**
	 * @throws IllegalArgumentException {@code from} 이 {@code to} 보다 앞이 아닐 때
	 */
	public StoreCoverage {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		Objects.requireNonNull(storeThreshold, "storeThreshold");
		if (!from.isBefore(to)) {
			throw new IllegalArgumentException("덮은 범위는 from < to 다: " + from + " / " + to);
		}
	}

	boolean covers(Instant at) {
		return !at.isBefore(this.from) && at.isBefore(this.to);
	}

}
