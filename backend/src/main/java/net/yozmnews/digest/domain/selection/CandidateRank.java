package net.yozmnews.digest.domain.selection;

import java.util.Comparator;
import java.util.OptionalDouble;

import net.yozmnews.tag.TopicStatus;

/**
 * 구독 후보의 순위 — 정본 — docs/specs/digest.md §5
 * <p>
 * 점수 = 중요도 × ({@code low_confidence} 면 {@link SelectionRules#LOW_CONFIDENCE_WEIGHT}) × 관심도({@link SelectionRules#NEUTRAL_INTEREST}).
 * 순서는 점수 내림차순(중요도가 없으면 맨 뒤) → 발행 시각 내림차순 → 기사 ID 오름차순.
 */
public final class CandidateRank {

	/** 순위 — 앞이 먼저 */
	public static final Comparator<ArticleFacts> ORDER = Comparator
		.comparing((ArticleFacts article) -> score(article).isEmpty())
		.thenComparing(article -> score(article).orElse(0), Comparator.reverseOrder())
		.thenComparing(ArticleFacts::publishedAt, Comparator.reverseOrder())
		.thenComparingLong(article -> article.id().value());

	private CandidateRank() {
	}

	/** 점수 — 중요도가 없으면 빈 값 */
	public static OptionalDouble score(ArticleFacts article) {
		Double importance = article.importance();
		if (importance == null) {
			return OptionalDouble.empty();
		}
		double weight = (article.topicStatus() == TopicStatus.LOW_CONFIDENCE) ? SelectionRules.LOW_CONFIDENCE_WEIGHT
				: 1.0;
		return OptionalDouble.of(importance * weight * SelectionRules.NEUTRAL_INTEREST);
	}

}
