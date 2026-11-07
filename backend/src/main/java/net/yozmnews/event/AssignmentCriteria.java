package net.yozmnews.event;

import java.time.Duration;
import java.util.Objects;

/**
 * 사건 기준 · 저장 기준 — docs/specs/event.md §3 · event-R17. 기본값의 정본은 설정({@code EventAssignmentProperties})이고, 이 타입은 검증과
 * 운반을 맡는다.
 *
 * @param window 시간 창 — 기사의 발행 시각과 사건의 첫 · 마지막 기사 시각의 차가 이 안이어야 후보다
 * @param upper 위 기준 — 점수가 이 이상이면 산식으로 붙인다
 * @param lower 아래 기준 — 점수가 이 이상이고 위 기준 미만이면 사건 소속 판정에 묻는다(띠)
 * @param store 저장 기준 — 점수가 이 이상인 후보를 점수 · 확률과 함께 남긴다(재판정의 재료)
 * @param probability 확률 기준 — 띠의 후보는 판정 확률이 이 이상이어야 붙인다
 */
public record AssignmentCriteria(Duration window, double upper, double lower, double store, double probability) {

	/**
	 * @throws IllegalArgumentException 창이 0 이하이거나 초 단위가 아니거나(기준 이력이 초로 적는다),
	 * {@code 0 ≤ store ≤ lower ≤ upper ≤ 1} 이 아니거나, 확률 기준이 {@code (0, 1]} 밖일 때 — 값이 숫자가 아니어도(NaN) 같다
	 */
	public AssignmentCriteria {
		Objects.requireNonNull(window, "window");
		if (window.isZero() || window.isNegative() || window.getNano() != 0) {
			throw new IllegalArgumentException("시간 창은 0 보다 큰 초 단위여야 한다: " + window);
		}
		if (!(0 <= store && store <= lower && lower <= upper && upper <= 1)) {
			throw new IllegalArgumentException("기준은 0 ≤ 저장 기준 ≤ 아래 기준 ≤ 위 기준 ≤ 1 이어야 한다: 저장 " + store + " · 아래 "
					+ lower + " · 위 " + upper);
		}
		if (!(0 < probability && probability <= 1)) {
			throw new IllegalArgumentException("확률 기준은 0 보다 크고 1 이하여야 한다: " + probability);
		}
	}

}
