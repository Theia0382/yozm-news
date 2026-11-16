package net.yozmnews.user.application;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.RemovedSubscription;
import net.yozmnews.user.domain.notice.StorylineSubscriptionsRemovedContent;
import org.springframework.stereotype.Component;

/**
 * 사라진 구독 알림의 제목 읽기 — 알림이 가리키는 사건의 지금 대표 기사 제목. docs/specs/user.md §4 · user-R54
 * <p>
 * 알림은 사건 ID 만 담고 제목은 볼 때 읽는다 — 기사가 지워졌으면 제목이 없다(지운 기사의 제목이 알림에 남지 않는다). 사건이 합쳐졌으면 남는
 * 사건의 대표 기사다. 부르는 쪽의 트랜잭션 안에서 읽는다.
 */
@Component
class RemovedSubscriptionTitles {

	private final EventDirectory events;

	private final ArticleDirectory articles;

	RemovedSubscriptionTitles(EventDirectory events, ArticleDirectory articles) {
		this.events = Objects.requireNonNull(events, "events");
		this.articles = Objects.requireNonNull(articles, "articles");
	}

	/**
	 * @return 알림들이 가리키는 사건 ID → 지금 대표 기사 제목 — 사건 · 대표 기사 · 기사가 없으면 빠진다. 이 종류의 알림이 없으면 빈 맵
	 */
	Map<Long, String> of(List<Notice> notices) {
		Set<Long> pointed = new LinkedHashSet<>();
		for (Notice notice : notices) {
			if (notice.content() instanceof StorylineSubscriptionsRemovedContent removed) {
				removed.removed()
					.stream()
					.map(RemovedSubscription::eventId)
					.filter(Objects::nonNull)
					.forEach(pointed::add);
			}
		}
		if (pointed.isEmpty()) {
			return Map.of();
		}
		// 합쳐진 사건은 남는 사건으로 — 대표 기사 조회는 합쳐진 사건을 빼므로 먼저 푼다
		Map<Long, EventId> survivors = new LinkedHashMap<>();
		for (long event : pointed) {
			this.events.find(new EventId(event)).ifPresent(view -> survivors.put(event, view.id()));
		}
		Map<EventId, ArticleId> representatives = this.events.representativesOfAll(Set.copyOf(survivors.values()));
		Map<ArticleId, ArticleContent> contents = this.articles.findAll(Set.copyOf(representatives.values()));
		Map<Long, String> titles = new LinkedHashMap<>();
		survivors.forEach((event, survivor) -> {
			ArticleId article = representatives.get(survivor);
			ArticleContent content = (article == null) ? null : contents.get(article);
			if (content != null) {
				titles.put(event, content.title());
			}
		});
		return titles;
	}

}
