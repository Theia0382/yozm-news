package net.yozmnews.storyline.domain.linking;

import java.time.Duration;
import java.util.Objects;

/**
 * 잇기 기준 — 기본값의 정본은 설정({@code LinkingProperties})이고, 이 타입은 검증과 운반을 맡는다 · docs/specs/storyline.md §4 ·
 * storyline-R9 · storyline-R36.
 * <p>
 * 점수가 위 기준 이상이면 묻지 않고 붙이고, 띠 [아래 기준, 위 기준) 안이면 흐름 소속 판정의 확률이 확률 기준 이상일 때 붙인다. 아래
 * 기준 미만은 붙이지 않는다. 아래 기준이 위 기준과 같으면 띠가 비어 점수만으로 정한다.
 *
 * @param window 시간 창 — 판정할 사건의 시각 범위에서 이만큼 떨어진 것까지 견준다
 * @param lower 아래 기준 — 이 이상이어야 판정할 수 있다. 코사인 하한 · 다시 넣기의 문턱이다
 * @param upper 위 기준 — 이 이상이면 묻지 않고 붙이거나 짝으로 연다
 * @param probability 확률 기준 — 띠 안 후보는 확률이 이 이상이어야 붙인다
 */
public record LinkingCriteria(Duration window, double lower, double upper, double probability) {

	/**
	 * @throws IllegalArgumentException 창이 0 이하이거나 초 단위가 아니거나(기준 이력이 초로 적는다), {@literal 0 < 아래 기준 ≤ 위 기준 ≤ 1} 이
	 * 아니거나, 확률 기준이 0 보다 크고 1 이하가 아닐 때 — 값이 숫자가 아니어도(NaN) 같다
	 */
	public LinkingCriteria {
		Objects.requireNonNull(window, "window");
		if (window.isZero() || window.isNegative() || window.getNano() != 0) {
			throw new IllegalArgumentException("시간 창은 0 보다 큰 초 단위여야 한다: " + window);
		}
		if (!(0 < lower && lower <= upper && upper <= 1)) {
			throw new IllegalArgumentException("기준은 0 < 아래 기준 ≤ 위 기준 ≤ 1 이어야 한다: " + lower + " · " + upper);
		}
		if (!(0 < probability && probability <= 1)) {
			throw new IllegalArgumentException("확률 기준은 0 보다 크고 1 이하여야 한다: " + probability);
		}
	}

	/**
	 * 점수가 띠 [아래 기준, 위 기준) 안인가 — 띠 판정의 정본. 아래 기준이 위 기준과 같으면(띠가 비면) 언제나 아니다. NaN 은 어디에도
	 * 들지 않는다
	 */
	public boolean inBand(Score score) {
		return score.reaches(this.lower) && !score.reaches(this.upper);
	}

}
