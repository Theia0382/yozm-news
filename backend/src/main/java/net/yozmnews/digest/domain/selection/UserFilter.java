package net.yozmnews.digest.domain.selection;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.source.SourceId;
import net.yozmnews.user.DigestInterests;
import net.yozmnews.user.TopicSubscription;

/**
 * 사용자의 구독 행과 제외 — 구독 필터와 큰 슬롯 자격의 판정 정본 — docs/specs/digest.md §5
 * <p>
 * 형식 · 소스는 기사마다({@link #passesArticleExclusions}), 지역 · 싫어요는 사건으로({@link #passesEvent}), 구독 행은 사건의 값으로
 * ({@link #subscriptions}) 본다.
 *
 * @param topics 구독 행 — 키 순
 * @param excludedRegions 제외한 지역 — 활성 키
 * @param excludedFormats 제외한 형식 — 활성 키
 * @param excludedSources 제외한 소스
 * @param dislikedEvents 그 사용자의 값이 싫어요인 사건 — {@code feedback} 이 합쳐진 사건을 풀어 마지막으로 누른 값으로 답한 것
 */
public record UserFilter(List<TopicSubscription> topics, Set<String> excludedRegions, Set<String> excludedFormats,
		Set<SourceId> excludedSources, Set<EventId> dislikedEvents) {

	public UserFilter {
		topics = List.copyOf(Objects.requireNonNull(topics, "topics"));
		excludedRegions = Set.copyOf(Objects.requireNonNull(excludedRegions, "excludedRegions"));
		excludedFormats = Set.copyOf(Objects.requireNonNull(excludedFormats, "excludedFormats"));
		excludedSources = Set.copyOf(Objects.requireNonNull(excludedSources, "excludedSources"));
		dislikedEvents = Set.copyOf(Objects.requireNonNull(dislikedEvents, "dislikedEvents"));
	}

	/**
	 * @param disliked 그 사용자의 값이 싫어요인 사건
	 */
	public static UserFilter of(DigestInterests interests, Collection<EventId> disliked) {
		return new UserFilter(interests.topics(), interests.excludedRegions(), interests.excludedFormats(),
				interests.excludedSources(), Set.copyOf(disliked));
	}

	/** 싫어요인 사건을 더한 필터 — 나중에 정해지는 사건(큰 슬롯)의 싫어요를 더한다 */
	public UserFilter withDisliked(Collection<EventId> more) {
		Objects.requireNonNull(more, "more");
		Set<EventId> disliked = new HashSet<>(this.dislikedEvents);
		disliked.addAll(more);
		return new UserFilter(this.topics, this.excludedRegions, this.excludedFormats, this.excludedSources, disliked);
	}

	/**
	 * 기사가 사용자의 기사 제외를 지나는가 — 형식(활성 키로 푼 것)이 제외와 겹치지 않고, 소스가 있으며 그 소스가 모두 제외는 아니고(소스가
	 * 여럿인 기사는 제외하지 않은 소스도 그 기사를 냈다). 기사의 지역은 보지 않는다 — 지역 제외와 싫어요는 사건으로 본다({@link #passesEvent}).
	 * 구독 사건과 큰 슬롯 사건의 기사가 같은 판정을 쓴다
	 */
	public boolean passesArticleExclusions(ArticleFacts article) {
		Objects.requireNonNull(article, "article");
		return Collections.disjoint(article.formats(), this.excludedFormats) && !article.sources().isEmpty()
				&& !this.excludedSources.containsAll(article.sources());
	}

	/**
	 * 사건이 사용자의 사건 제외를 지나는가 — 사건의 지역({@link EventFacts#regions()})이 제외한 지역과 겹치지 않고, 그 사용자의 값이 싫어요인
	 * 사건이 아니다. 구독 사건과 큰 슬롯 사건이 같은 판정을 쓴다
	 */
	public boolean passesEvent(EventFacts event) {
		Objects.requireNonNull(event, "event");
		return Collections.disjoint(event.regions(), this.excludedRegions) && !this.dislikedEvents.contains(event.id());
	}

	/** 사건과 겹치는 구독 행의 키 — 행 순서. 사건의 주제 중분류가 그 행이 덮는 중분류와 하나라도 겹치면 겹친다 */
	public List<String> subscriptions(EventFacts event) {
		Objects.requireNonNull(event, "event");
		return this.topics.stream()
			.filter(topic -> !Collections.disjoint(topic.subtopics(), event.subtopics()))
			.map(TopicSubscription::key)
			.toList();
	}

}
