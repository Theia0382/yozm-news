package net.yozmnews.digest.domain.selection;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 구독 필터 — 정본 — docs/specs/digest.md §5
 * <p>
 * 첫 평가 시각이 기간 안인 기사 가운데 발행 시각이 기간 시작 − {@link SelectionRules#PUBLISHED_LOOKBACK} 이상이고(경계 포함), 사용자의
 * 제외를 지나고, 구독 행과 겹치는 기사. 미분류 기사는 붙은 주제가 없어 겹치지 않는다. 결과는 {@link CandidateRank} 순서다.
 */
public final class SubscriptionFilter {

	private SubscriptionFilter() {
	}

	/**
	 * @param firstEvaluated 첫 평가 시각이 기간 안인 기사
	 * @param periodStart 읽은 기간의 시작 — 조회 기간을 상한으로 줄였으면 줄인 시작
	 */
	public static List<ArticleFacts> select(Collection<ArticleFacts> firstEvaluated, UserFilter filter,
			Instant periodStart) {
		Objects.requireNonNull(firstEvaluated, "firstEvaluated");
		Objects.requireNonNull(filter, "filter");
		Instant earliest = Objects.requireNonNull(periodStart, "periodStart").minus(SelectionRules.PUBLISHED_LOOKBACK);
		return firstEvaluated.stream()
			.filter(article -> !article.publishedAt().isBefore(earliest))
			.filter(filter::passesExclusions)
			.filter(article -> !filter.subscriptions(article).isEmpty())
			.sorted(CandidateRank.ORDER)
			.toList();
	}

}
