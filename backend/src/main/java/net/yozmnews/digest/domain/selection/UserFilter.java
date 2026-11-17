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
	 * 사용자의 제외를 지나는가 — 붙은 지역이 제외와 겹치지 않고, 형식(활성 키로 푼 것)이 제외와 겹치지 않고, 소스가 있으며 그 소스가 모두 제외는
	 * 아니다(소스가 여럿인 기사는 제외하지 않은 소스도 그 기사를 냈다)
	 */
	public boolean passesExclusions(ArticleFacts article) {
		Objects.requireNonNull(article, "article");
		return Collections.disjoint(article.regions(), this.excludedRegions)
				&& Collections.disjoint(article.formats(), this.excludedFormats) && !article.sources().isEmpty()
				&& !this.excludedSources.containsAll(article.sources());
	}

	/** 기사와 겹치는 구독 행의 키 — 행 순서. 붙은 중분류가 그 행이 덮는 중분류와 하나라도 겹치면 겹친다 */
	public List<String> subscriptions(ArticleFacts article) {
		Objects.requireNonNull(article, "article");
		return this.topics.stream()
			.filter(topic -> !Collections.disjoint(topic.subtopics(), article.subtopics()))
			.map(TopicSubscription::key)
			.toList();
	}

}
