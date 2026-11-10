package net.yozmnews.storyline.domain.linking;

import java.time.Duration;
import java.util.Objects;

/**
 * 잇기 기준 — 기본값의 정본은 설정({@code LinkingProperties})이고, 이 타입은 검증과 운반을 맡는다 · docs/specs/storyline.md §4 ·
 * storyline-R9.
 *
 * @param window 시간 창 — 판정할 사건의 시각 범위에서 이만큼 떨어진 것까지 견준다
 * @param threshold 기준 점수 — 이 이상이어야 붙이거나 짝으로 연다
 */
public record LinkingCriteria(Duration window, double threshold) {

	/**
	 * @throws IllegalArgumentException 창이 0 이하이거나 초 단위가 아니거나(기준 이력이 초로 적는다), 기준이 0 보다 크고 1 이하가 아닐 때
	 * — 값이 숫자가 아니어도(NaN) 같다
	 */
	public LinkingCriteria {
		Objects.requireNonNull(window, "window");
		if (window.isZero() || window.isNegative() || window.getNano() != 0) {
			throw new IllegalArgumentException("시간 창은 0 보다 큰 초 단위여야 한다: " + window);
		}
		if (!(0 < threshold && threshold <= 1)) {
			throw new IllegalArgumentException("기준 점수는 0 보다 크고 1 이하여야 한다: " + threshold);
		}
	}

}
