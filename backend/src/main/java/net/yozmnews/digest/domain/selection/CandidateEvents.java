package net.yozmnews.digest.domain.selection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;

/**
 * 후보 사건 꾸리기 — 사건마다 그 사용자에게 그 기간에 든 기사를 모은다. 정본 · docs/specs/digest.md §5 · digest-R17 · digest-R19
 * <p>
 * 후보는 사건이다 — 사건에 배정되지 않은 기사는 그 기간의 후보가 아니다. 기사 제외({@link UserFilter#passesArticleExclusions})는 기사마다 거르고,
 * 사건이 후보로 남는지는 사건의 값으로 정한다.
 */
public final class CandidateEvents {

	private CandidateEvents() {
	}

	/**
	 * 구독 후보의 재료 — 첫 평가 시각이 기간 안인 기사 가운데 발행 시각이 기간 시작 − {@link SelectionRules#PUBLISHED_LOOKBACK} 이상이고
	 * (경계 포함) 기사 제외를 지난 기사를 사건마다 모은다. 사건이 없는 기사와 값이 없는 사건(평가 있는 소속 기사가 없는 사건)은 버린다. 사건
	 * 제외(지역 · 싫어요) · 구독 행은 보지 않는다 — {@link SubscriptionFilter} 가 본다.
	 * @param period 첫 평가 시각이 기간 안인 기사
	 * @param eventOf 기사 → 사건 — 배정이 없는 기사는 빠져 있다
	 * @param facts 사건 → 사건의 값
	 * @param tops 사건 → 꼭대기 — 키가 없는 사건은 꼭대기가 없다
	 * @param periodStart 읽은 기간의 시작 — 조회 기간을 상한으로 줄였으면 줄인 시작
	 * @return 사건 ID 순
	 */
	public static List<CandidateEvent> subscribed(Collection<ArticleFacts> period, Map<ArticleId, EventId> eventOf,
			Map<EventId, EventFacts> facts, Map<EventId, List<StorylineId>> tops, UserFilter filter,
			Instant periodStart) {
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(eventOf, "eventOf");
		Objects.requireNonNull(facts, "facts");
		Objects.requireNonNull(tops, "tops");
		Objects.requireNonNull(filter, "filter");
		Instant earliest = Objects.requireNonNull(periodStart, "periodStart").minus(SelectionRules.PUBLISHED_LOOKBACK);
		Map<Long, List<ArticleFacts>> byEvent = new TreeMap<>();
		Map<Long, EventFacts> eventFacts = new TreeMap<>();
		for (ArticleFacts article : period) {
			EventId event = eventOf.get(article.id());
			EventFacts ofEvent = (event == null) ? null : facts.get(event);
			if (ofEvent == null || article.publishedAt().isBefore(earliest) || !filter.passesArticleExclusions(article)) {
				continue;
			}
			byEvent.computeIfAbsent(event.value(), ignored -> new ArrayList<>()).add(article);
			eventFacts.put(event.value(), ofEvent);
		}
		List<CandidateEvent> events = new ArrayList<>(byEvent.size());
		byEvent.forEach((event, articles) -> events.add(new CandidateEvent(eventFacts.get(event),
				tops.getOrDefault(new EventId(event), List.of()), articles)));
		return List.copyOf(events);
	}

	/**
	 * 큰 판정 행의 사건 하나의 후보 — 그 사건의 기간 안(발행 시각) 기사 가운데 기사 제외를 지난 기사. 사건이 사건 제외(지역 · 싫어요 · 거른
	 * 꼭대기 — {@link UserFilter#passesEvent})를 지나지 못하거나 지난 기사가 없으면 후보가 없다
	 * @param tops 그 사건의 꼭대기 — 없으면 빈 목록
	 * @param inPeriod 그 사건의 기간 안 기사 가운데 평가가 있는 기사
	 * @return 후보 사건 — 없으면 null
	 */
	public static @Nullable CandidateEvent big(EventFacts facts, List<StorylineId> tops,
			Collection<ArticleFacts> inPeriod, UserFilter filter) {
		Objects.requireNonNull(facts, "facts");
		Objects.requireNonNull(tops, "tops");
		Objects.requireNonNull(inPeriod, "inPeriod");
		Objects.requireNonNull(filter, "filter");
		if (!filter.passesEvent(facts, tops)) {
			return null;
		}
		List<ArticleFacts> passed = inPeriod.stream().filter(filter::passesArticleExclusions).toList();
		return passed.isEmpty() ? null : new CandidateEvent(facts, tops, passed);
	}

}
