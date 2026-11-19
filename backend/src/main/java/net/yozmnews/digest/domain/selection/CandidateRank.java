package net.yozmnews.digest.domain.selection;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.OptionalDouble;

import net.yozmnews.digest.domain.interest.TopicInterests;

/**
 * 후보 사건의 점수와 순위 — 정본 — docs/specs/digest.md §5
 * <p>
 * 점수 = 사건 중요도 × ({@code low_confidence} 면 {@link SelectionRules#LOW_CONFIDENCE_WEIGHT}) × 관심도 가중치(사건 주제 중분류의
 * {@link TopicInterests#weightOf}). 순서는 점수 내림차순(중요도가 없으면 맨 뒤) → 후보 사건의 기사({@link CandidateEvent#articles()})
 * 가운데 가장 늦은 발행 시각 내림차순 → 사건 ID 오름차순. 관심도를 보지 않는 순위는 {@link TopicInterests#NEUTRAL_ALL} 로 만든다. 항목
 * 점수도 이 점수를 쓴다.
 */
public final class CandidateRank {

	private CandidateRank() {
	}

	/** 순위 — 앞이 먼저 */
	public static Comparator<CandidateEvent> order(TopicInterests interests) {
		Objects.requireNonNull(interests, "interests");
		return Comparator.comparing((CandidateEvent event) -> score(event, interests).isEmpty())
			.thenComparing(event -> score(event, interests).orElse(0), Comparator.reverseOrder())
			.thenComparing(CandidateRank::latestPublished, Comparator.reverseOrder())
			.thenComparingLong(event -> event.facts().id().value());
	}

	/** 사건 점수 — 사건 중요도가 없으면 빈 값 */
	public static OptionalDouble score(CandidateEvent event, TopicInterests interests) {
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(interests, "interests");
		EventFacts facts = event.facts();
		Double importance = facts.importance();
		if (importance == null) {
			return OptionalDouble.empty();
		}
		double weight = facts.lowConfidence() ? SelectionRules.LOW_CONFIDENCE_WEIGHT : 1.0;
		return OptionalDouble.of(importance * weight * interests.weightOf(facts.subtopics()));
	}

	private static Instant latestPublished(CandidateEvent event) {
		return event.articles().getLast().publishedAt();
	}

}
