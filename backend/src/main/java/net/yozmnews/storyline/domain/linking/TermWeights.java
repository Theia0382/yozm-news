package net.yozmnews.storyline.domain.linking;

import java.util.Map;

/**
 * 말의 무게의 정본 — 많은 사건에 있는 말일수록 작다 · docs/specs/storyline.md §5 · storyline-R8.
 * <p>
 * 무게 = ln((사건 수 + 1) ÷ (그 말을 가진 사건 수 + 1)), 0 이상. 수는 판정할 사건의 구간과 겹치는 사건으로 센 값이다 — 세는 일은
 * {@code event} 가 하고 이 클래스는 수를 무게로 바꾸기만 한다.
 */
public final class TermWeights {

	private final long events;

	private final Map<String, Long> eventsByTerm;

	private TermWeights(long events, Map<String, Long> eventsByTerm) {
		this.events = events;
		this.eventsByTerm = eventsByTerm;
	}

	/**
	 * @param events 사건 수
	 * @param eventsByTerm 말 → 그 말을 핵심 말로 가진 사건 수
	 * @throws IllegalArgumentException 사건 수가 음수일 때
	 */
	public static TermWeights of(long events, Map<String, Long> eventsByTerm) {
		if (events < 0) {
			throw new IllegalArgumentException("사건 수는 0 이상이다: " + events);
		}
		return new TermWeights(events, Map.copyOf(eventsByTerm));
	}

	/**
	 * 말 하나의 무게 — 수를 모르는 말은 어느 사건에도 없는 말로 본다. 그 말을 가진 사건 수가 사건 수 이상이면 0 이다(두 수를 따로 세어 어긋날
	 * 수 있다).
	 */
	public double weight(String term) {
		long having = this.eventsByTerm.getOrDefault(term, 0L);
		return Math.max(0, Math.log((this.events + 1.0) / (having + 1.0)));
	}

}
