package net.yozmnews.digest.domain.selection;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.domain.interest.TopicInterests;

/**
 * 구독 필터 — 정본 — docs/specs/digest.md §5
 * <p>
 * 후보 사건({@link CandidateEvents#subscribed}) 가운데 사건의 지역이 제외와 겹치지 않고 사건의 주제가 구독 행과 겹치는 사건. 주제가 없는
 * 사건은 겹치지 않는다. 결과는 {@link CandidateRank} 순서다.
 */
public final class SubscriptionFilter {

	private SubscriptionFilter() {
	}

	/**
	 * @param events 후보 사건
	 * @param interests 순위의 관심도 가중치 — 관심도를 보지 않으면 {@link TopicInterests#NEUTRAL_ALL}
	 */
	public static List<CandidateEvent> select(Collection<CandidateEvent> events, UserFilter filter,
			TopicInterests interests) {
		Objects.requireNonNull(events, "events");
		Objects.requireNonNull(filter, "filter");
		return events.stream()
			.filter(event -> filter.passesRegions(event.facts()))
			.filter(event -> !filter.subscriptions(event.facts()).isEmpty())
			.sorted(CandidateRank.order(interests))
			.toList();
	}

}
