package net.yozmnews.reader.adapter.in.web;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;

/**
 * 사건의 대표 기사 맞붙이기 — docs/specs/reader.md §5 reader-R3 · R4.
 * <p>
 * 대표는 사건 기사 전체에서 고른 것이다({@link EventDirectory#representativesOfAll} — 운영자 화면과 같은 정본). 한 응답에 한 번 부른다 — 카드 ·
 * 타임라인 사건 · 관계 상대의 대표 사건을 모아 넘긴다.
 */
final class StorylineTitles {

	private final EventDirectory events;

	private final ScreenArticles articles;

	StorylineTitles(EventDirectory events, ScreenArticles articles) {
		this.events = Objects.requireNonNull(events, "events");
		this.articles = Objects.requireNonNull(articles, "articles");
	}

	/** 사건마다 대표 기사 — 대표가 없거나(합쳐짐 · 지워짐) 기사가 지워진 사건은 빠진다. 빈 입력이면 부르지 않는다 */
	Map<EventId, NewsArticleResponse> of(Collection<EventId> events) {
		List<EventId> distinct = events.stream().distinct().toList();
		if (distinct.isEmpty()) {
			return Map.of();
		}
		Map<EventId, ArticleId> representatives = this.events.representativesOfAll(distinct);
		Map<ArticleId, NewsArticleResponse> headlines = this.articles.of(representatives.values());
		Map<EventId, NewsArticleResponse> found = new LinkedHashMap<>();
		representatives.forEach((event, article) -> {
			NewsArticleResponse headline = headlines.get(article);
			if (headline != null) {
				found.put(event, headline);
			}
		});
		return found;
	}

}
