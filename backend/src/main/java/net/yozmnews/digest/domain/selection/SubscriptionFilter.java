package net.yozmnews.digest.domain.selection;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.domain.interest.InterestWeights;

/**
 * 구독 필터 — 정본 — docs/specs/digest.md §5
 * <p>
 * 후보 사건({@link CandidateEvents#subscribed}) 가운데 사건 제외({@link UserFilter#passesEvent} — 지역 · 싫어요 · 거른 꼭대기)를
 * 지나고 사건의 주제가 구독 행과 겹치는 사건. 주제가 없는 사건은 겹치지 않는다. 결과는 {@link CandidateRank} 순서다.
 */
public final class SubscriptionFilter {

	private SubscriptionFilter() {
	}

	/**
	 * @param events 후보 사건
	 * @param weights 순위의 관심도 가중치 — 관심도를 보지 않으면 {@link InterestWeights#NEUTRAL_ALL}
	 */
	public static List<CandidateEvent> select(Collection<CandidateEvent> events, UserFilter filter,
			InterestWeights weights) {
		Objects.requireNonNull(events, "events");
		Objects.requireNonNull(filter, "filter");
		return events.stream()
			.filter(event -> filter.passesEvent(event.facts(), event.tops()))
			.filter(event -> !filter.subscriptions(event.facts()).isEmpty())
			.sorted(CandidateRank.order(weights))
			.toList();
	}

}
