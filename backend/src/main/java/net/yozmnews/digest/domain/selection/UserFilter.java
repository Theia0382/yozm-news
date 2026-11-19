package net.yozmnews.digest.domain.selection;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.source.SourceId;
import net.yozmnews.user.DigestInterests;
import net.yozmnews.user.TopicSubscription;

/**
 * 사용자의 구독 행과 제외 — 구독 필터와 큰 슬롯 자격의 판정 정본 — docs/specs/digest.md §5
 * <p>
 * 형식 · 소스 · 싫어요는 기사마다({@link #passesArticleExclusions}), 지역 제외와 구독 행은 사건의 값으로({@link #passesRegions} ·
 * {@link #subscriptions}) 본다.
 *
 * @param topics 구독 행 — 키 순
 * @param excludedRegions 제외한 지역 — 활성 키
 * @param excludedFormats 제외한 형식 — 활성 키
 * @param excludedSources 제외한 소스
 */
public record UserFilter(List<TopicSubscription> topics, Set<String> excludedRegions, Set<String> excludedFormats,
		Set<SourceId> excludedSources) {

	public UserFilter {
		topics = List.copyOf(Objects.requireNonNull(topics, "topics"));
		excludedRegions = Set.copyOf(Objects.requireNonNull(excludedRegions, "excludedRegions"));
		excludedFormats = Set.copyOf(Objects.requireNonNull(excludedFormats, "excludedFormats"));
		excludedSources = Set.copyOf(Objects.requireNonNull(excludedSources, "excludedSources"));
	}

	public static UserFilter of(DigestInterests interests) {
		return new UserFilter(interests.topics(), interests.excludedRegions(), interests.excludedFormats(),
				interests.excludedSources());
	}

	/**
	 * 기사가 사용자의 기사 제외를 지나는가 — 형식(활성 키로 푼 것)이 제외와 겹치지 않고, 소스가 있으며 그 소스가 모두 제외는 아니고(소스가
	 * 여럿인 기사는 제외하지 않은 소스도 그 기사를 냈다), 그 사용자가 싫어요를 누른 기사가 아니다. 기사의 지역은 보지 않는다 — 지역 제외는 사건의
	 * 지역으로 본다({@link #passesRegions}). 구독 사건과 큰 슬롯 사건의 기사가 같은 판정을 쓴다
	 */
	public boolean passesArticleExclusions(ArticleFacts article) {
		Objects.requireNonNull(article, "article");
		return Collections.disjoint(article.formats(), this.excludedFormats) && !article.sources().isEmpty()
				&& !this.excludedSources.containsAll(article.sources()) && !article.disliked();
	}

	/** 사건의 지역({@link EventFacts#regions()})이 제외한 지역과 겹치지 않는가 */
	public boolean passesRegions(EventFacts event) {
		Objects.requireNonNull(event, "event");
		return Collections.disjoint(event.regions(), this.excludedRegions);
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
