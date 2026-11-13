package net.yozmnews.storyline.domain.relation;

import java.time.Duration;
import java.util.Objects;

/**
 * 관계 기준 — docs/specs/storyline.md §4 · storyline-R27 · storyline-R29 · §7.
 *
 * @param probability 확률 기준 — 가장 높은 답의 확률이 이 이상이어야 맺는다
 * @param oneMatter 한 사안 기준 — 두 스토리라인의 "한 사안" 확률 가운데 작은 쪽의 하한
 * @param minEvents 후보가 되는 스토리라인의 구성 사건 수 하한
 * @param candidateScore 점수 후보의 기준
 * @param window 점수 후보를 찾고 말 수를 세는 시간 창
 */
public record RelationCriteria(double probability, double oneMatter, int minEvents, double candidateScore,
		Duration window) {

	/** 사건 수 하한의 하한 — 스토리라인은 서로 다른 사건 둘 이상이다 */
	public static final int MIN_EVENTS_FLOOR = 2;

	/**
	 * @throws IllegalArgumentException 확률 기준 · 후보 점수 기준이 0 보다 크고 1 이하가 아니거나, 한 사안 기준이 0 ~ 1 밖이거나, 사건 수
	 * 하한이 {@value #MIN_EVENTS_FLOOR} 미만이거나, 창이 0 이하이거나 초 단위가 아닐 때
	 */
	public RelationCriteria {
		if (!(probability > 0 && probability <= 1)) {
			throw new IllegalArgumentException("확률 기준은 0 보다 크고 1 이하다: " + probability);
		}
		if (!(oneMatter >= 0 && oneMatter <= 1)) {
			throw new IllegalArgumentException("한 사안 기준은 0 이상 1 이하다: " + oneMatter);
		}
		if (minEvents < MIN_EVENTS_FLOOR) {
			throw new IllegalArgumentException("사건 수 하한은 " + MIN_EVENTS_FLOOR + " 이상이다: " + minEvents);
		}
		if (!(candidateScore > 0 && candidateScore <= 1)) {
			throw new IllegalArgumentException("후보 점수 기준은 0 보다 크고 1 이하다: " + candidateScore);
		}
		Objects.requireNonNull(window, "window");
		if (window.isZero() || window.isNegative() || window.getNano() != 0) {
			throw new IllegalArgumentException("시간 창은 0 보다 큰 초 단위다: " + window);
		}
	}

}
