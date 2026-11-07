package net.yozmnews.event.domain.assignment;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.event.AssignmentCriteria;

/**
 * 후보 점수 산식의 정본 — docs/specs/event.md §4 · event-R10.
 * <p>
 * 점수 = 감쇠 × ((1 − {@link #KEY_TERM_WEIGHT}) × max(코사인, 0) + {@link #KEY_TERM_WEIGHT} × 핵심 말 겹침).
 * <ul>
 * <li>시각 차 Δ — 구간 [from, to] 가 사건의 마지막 기사 시각 뒤면 from − 마지막, 첫 기사 시각 앞이면 첫 − to, 겹치면 0. 기사는 발행 시각
 * 하나의 점 구간이고, 병합은 사건의 첫 ~ 마지막 기사 시각이다. Δ 가 창보다 크면 후보가 아니다</li>
 * <li>감쇠 = 2^(−Δ / {@link #HALF_LIFE})</li>
 * <li>핵심 말 겹침 = |기준 핵심 말 ∩ 사건 핵심 말| ÷ |기준 핵심 말| — 기준 핵심 말이 없으면 0. 배정은 기사의 핵심 말이 기준이다</li>
 * </ul>
 * 가중치 · 반감기 · 사건 핵심 말 수는 측정 전 목표다.
 */
public final class CandidateScoring {

	/** 핵심 말 겹침의 가중치 — 나머지는 코사인 유사도 몫 */
	public static final double KEY_TERM_WEIGHT = 0.3;

	/** 감쇠의 반감기 — 시각 차가 이만큼이면 점수가 반이 된다 */
	public static final Duration HALF_LIFE = Duration.ofHours(24);

	/** 산식에 쓰는 사건 핵심 말 수 — 기사 수가 많은 순(같으면 말 순) 상위. 저장소의 질의에 매개변수로 넘긴다 */
	public static final int EVENT_KEY_TERMS = 20;

	private CandidateScoring() {
	}

	/**
	 * 사건 하나의 점수를 매긴다.
	 * @param articleTerms 기사의 핵심 말(event-R2)
	 * @param publishedAt 기사의 발행 시각
	 * @param window 시간 창 — {@link AssignmentCriteria#window()}
	 * @return 점수 — 시각 차가 창보다 크면 빈 값
	 */
	public static Optional<ScoredCandidate> score(EventSnapshot event, List<String> articleTerms, Instant publishedAt,
			Duration window) {
		return score(event, articleTerms, publishedAt, publishedAt, window);
	}

	/**
	 * 구간 하나와 사건 하나의 점수를 매긴다 — 병합은 바뀐 사건의 첫 ~ 마지막 기사 시각을 구간으로 넘긴다(event-R13). 산식은
	 * {@link #score(EventSnapshot, List, Instant, Duration)} 와 하나다.
	 * @param terms 겹침의 기준 핵심 말
	 * @param from 구간의 시작 — {@code to} 이하
	 * @param to 구간의 끝
	 * @return 점수 — 시각 차가 창보다 크면 빈 값
	 * @throws IllegalArgumentException 시작이 끝보다 늦을 때
	 */
	public static Optional<ScoredCandidate> score(EventSnapshot event, List<String> terms, Instant from, Instant to,
			Duration window) {
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("구간의 시작이 끝보다 늦다: " + from + " > " + to);
		}
		Duration delta = delta(event, from, to);
		if (delta.compareTo(window) > 0) {
			return Optional.empty();
		}
		double decay = Math.pow(2, -seconds(delta) / seconds(HALF_LIFE));
		double similarity = (1 - KEY_TERM_WEIGHT) * Math.max(event.cosine(), 0) + KEY_TERM_WEIGHT
				* overlap(terms, event.keyTerms());
		return Optional.of(new ScoredCandidate(event.eventId(), decay * similarity));
	}

	private static Duration delta(EventSnapshot event, Instant from, Instant to) {
		if (from.isAfter(event.lastArticleAt())) {
			return Duration.between(event.lastArticleAt(), from);
		}
		if (to.isBefore(event.firstArticleAt())) {
			return Duration.between(to, event.firstArticleAt());
		}
		return Duration.ZERO;
	}

	private static double overlap(List<String> articleTerms, List<String> eventTerms) {
		Set<String> article = new HashSet<>(articleTerms);
		if (article.isEmpty()) {
			return 0;
		}
		Set<String> shared = new HashSet<>(article);
		shared.retainAll(new HashSet<>(eventTerms));
		return (double) shared.size() / article.size();
	}

	private static double seconds(Duration duration) {
		return duration.getSeconds() + duration.getNano() / 1e9;
	}

}
