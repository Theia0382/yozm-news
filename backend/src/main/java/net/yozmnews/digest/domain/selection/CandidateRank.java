package net.yozmnews.digest.domain.selection;

import java.util.Comparator;
import java.util.Objects;
import java.util.OptionalDouble;

import net.yozmnews.digest.domain.interest.TopicInterests;
import net.yozmnews.tag.TopicStatus;

/**
 * 구독 후보의 순위 — 정본 — docs/specs/digest.md §5
 * <p>
 * 점수 = 중요도 × ({@code low_confidence} 면 {@link SelectionRules#LOW_CONFIDENCE_WEIGHT}) × 관심도 가중치(그 기사 주제 중분류의
 * {@link TopicInterests#weightOf}). 순서는 점수 내림차순(중요도가 없으면 맨 뒤) → 발행 시각 내림차순 → 기사 ID 오름차순. 관심도를 보지
 * 않는 순위는 {@link TopicInterests#NEUTRAL_ALL} 로 만든다.
 */
public final class CandidateRank {

	private CandidateRank() {
	}

	/** 순위 — 앞이 먼저 */
	public static Comparator<ArticleFacts> order(TopicInterests interests) {
		Objects.requireNonNull(interests, "interests");
		return Comparator.comparing((ArticleFacts article) -> score(article, interests).isEmpty())
			.thenComparing(article -> score(article, interests).orElse(0), Comparator.reverseOrder())
			.thenComparing(ArticleFacts::publishedAt, Comparator.reverseOrder())
			.thenComparingLong(article -> article.id().value());
	}

	/** 점수 — 중요도가 없으면 빈 값 */
	public static OptionalDouble score(ArticleFacts article, TopicInterests interests) {
		Objects.requireNonNull(interests, "interests");
		Double importance = article.importance();
		if (importance == null) {
			return OptionalDouble.empty();
		}
		double weight = (article.topicStatus() == TopicStatus.LOW_CONFIDENCE) ? SelectionRules.LOW_CONFIDENCE_WEIGHT
				: 1.0;
		return OptionalDouble.of(importance * weight * interests.weightOf(article.subtopics()));
	}

}
