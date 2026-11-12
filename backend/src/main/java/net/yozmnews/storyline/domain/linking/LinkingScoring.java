package net.yozmnews.storyline.domain.linking;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 잇기 점수 산식 · 코사인 하한의 정본 — docs/specs/storyline.md §5 · storyline-R8.
 * <p>
 * 점수 = (1 − {@link #KEY_TERM_WEIGHT}) × max(코사인, 0) + {@link #KEY_TERM_WEIGHT} × 핵심 말 겹침.
 * <ul>
 * <li>시각 차 Δ — 두 시각 범위(첫 ~ 마지막 기사 시각)의 간격, 겹치면 0. Δ 가 창보다 크면 견줄 것이 아니다(같으면 견준다). 창 안이면 시각
 * 차는 점수를 바꾸지 않는다</li>
 * <li>핵심 말 겹침 = Σ 무게(판정할 사건 상위 핵심 말 ∩ 견줄 것 상위 핵심 말) ÷ Σ 무게(판정할 사건 상위 핵심 말) — 분모가 0 이면 0.
 * 무게는 {@link TermWeights}</li>
 * </ul>
 * 상수는 모두 측정 전 목표다.
 */
public final class LinkingScoring {

	/** 핵심 말 겹침의 가중치 — 나머지는 코사인 몫 */
	public static final double KEY_TERM_WEIGHT = 0.11;

	/** 산식에 쓰는 상위 핵심 말 수 — 사건 표현을 읽을 때와 스토리라인 핵심 말을 합할 때도 쓴다 */
	public static final int KEY_TERMS = 20;

	/** 이웃 조회 한 번의 수 — 코사인 순 상위. 자르기는 잇기 규칙의 정의다 */
	public static final int NEIGHBORS = 50;

	/** 반 정밀도 코사인의 여유 — 하한을 이만큼 낮춰 거른다 */
	public static final double COSINE_MARGIN = 0.01;

	private LinkingScoring() {
	}

	/**
	 * 판정할 사건과 견줄 것 하나의 점수.
	 * @param window 시간 창 — {@link LinkingCriteria#window()}
	 * @param weights 판정할 사건의 말의 무게
	 * @return 점수 — 시각 차가 창보다 크면 빈 값. 코사인이 NaN 이면 점수도 NaN
	 */
	public static Optional<Score> score(Subject subject, Compared other, Duration window, TermWeights weights) {
		if (delta(subject, other).compareTo(window) > 0) {
			return Optional.empty();
		}
		double overlap = overlap(subject, other, weights);
		double score = (1 - KEY_TERM_WEIGHT) * Math.max(other.cosine(), 0) + KEY_TERM_WEIGHT * overlap;
		return Optional.of(new Score(score, other.cosine(), overlap));
	}

	/**
	 * 기준에 닿을 수 있는 코사인의 하한 — 겹침 ≤ 1 이라 점수 ≥ 기준이면 코사인 ≥ (기준 − w) ÷ (1 − w) 다. 여기서
	 * {@link #COSINE_MARGIN} 을 뺀다.
	 * @return 기준이 가중치 이하면 −1(거르지 않는다)
	 */
	public static double cosineFloor(double threshold) {
		if (threshold <= KEY_TERM_WEIGHT) {
			return -1;
		}
		return (threshold - KEY_TERM_WEIGHT) / (1 - KEY_TERM_WEIGHT) - COSINE_MARGIN;
	}

	/** 판정할 사건의 시각 범위에서 창만큼 넓힌 구간의 시작 */
	public static Instant windowFrom(Instant firstArticleAt, Duration window) {
		return firstArticleAt.minus(window);
	}

	/** 판정할 사건의 시각 범위에서 창만큼 넓힌 구간의 끝 */
	public static Instant windowTo(Instant lastArticleAt, Duration window) {
		return lastArticleAt.plus(window);
	}

	private static Duration delta(Subject subject, Compared other) {
		if (subject.firstArticleAt().isAfter(other.lastArticleAt())) {
			return Duration.between(other.lastArticleAt(), subject.firstArticleAt());
		}
		if (subject.lastArticleAt().isBefore(other.firstArticleAt())) {
			return Duration.between(subject.lastArticleAt(), other.firstArticleAt());
		}
		return Duration.ZERO;
	}

	private static double overlap(Subject subject, Compared other, TermWeights weights) {
		Set<String> theirs = new HashSet<>(other.keyTerms());
		double all = 0;
		double shared = 0;
		for (String term : new HashSet<>(subject.keyTerms())) {
			double weight = weights.weight(term);
			all += weight;
			if (theirs.contains(term)) {
				shared += weight;
			}
		}
		return all > 0 ? shared / all : 0;
	}

}
