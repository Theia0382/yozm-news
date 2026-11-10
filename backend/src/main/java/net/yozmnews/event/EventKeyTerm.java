package net.yozmnews.event;

import java.util.Objects;

/**
 * 사건 핵심 말 하나 — docs/specs/event.md §3 · event-R12.
 *
 * @param term 말 — 비지 않는다
 * @param articleCount 그 말을 가진 구성 기사 수 — 1 이상
 */
public record EventKeyTerm(String term, int articleCount) {

	/**
	 * @throws IllegalArgumentException 말이 비었거나 수가 1 미만일 때
	 */
	public EventKeyTerm {
		Objects.requireNonNull(term, "term");
		if (term.isEmpty()) {
			throw new IllegalArgumentException("핵심 말이 비었다");
		}
		if (articleCount < 1) {
			throw new IllegalArgumentException("핵심 말의 기사 수는 1 이상이다: " + articleCount);
		}
	}

}
