package net.yozmnews.admin.adapter.in.web;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import org.jspecify.annotations.Nullable;

/**
 * 사건의 대표 기사와 그 정보 맞붙이기 — 정본 · docs/specs/admin.md §4 · admin-R6 · admin-R7.
 * <p>
 * 대표는 사건 기사 전체에서 고른다({@link EventDirectory#representativesOfAll} — 사건 화면과 같은 정본). 큰 스토리라인 · 스토리라인 목록 ·
 * 상세 · 타임라인 사건이 이것 하나를 쓰고, 한 응답에 한 번 부른다.
 */
final class AdminRepresentatives {

	private final EventDirectory events;

	private final AdminArticles articles;

	AdminRepresentatives(EventDirectory events, AdminArticles articles) {
		this.events = Objects.requireNonNull(events, "events");
		this.articles = Objects.requireNonNull(articles, "articles");
	}

	/** 사건마다 대표 기사와 그 요약 — 대표가 없거나(합쳐짐 · 지워짐) 기사가 지워졌으면 그 칸이 null */
	Map<EventId, Representative> of(Collection<EventId> events) {
		List<EventId> distinct = events.stream().distinct().toList();
		if (distinct.isEmpty()) {
			return Map.of();
		}
		Map<EventId, ArticleId> representatives = this.events.representativesOfAll(distinct);
		Map<ArticleId, AdminArticles.Summary> summaries = this.articles
			.summaries(representatives.values().stream().distinct().toList());
		Map<EventId, Representative> found = new LinkedHashMap<>();
		for (EventId event : distinct) {
			ArticleId article = representatives.get(event);
			found.put(event, new Representative(article, (article == null) ? null : summaries.get(article)));
		}
		return found;
	}

	/**
	 * 사건 하나의 대표.
	 *
	 * @param article 대표 기사 — 없으면 null
	 * @param summary 그 기사의 제목 · 출처 — 대표가 없거나 그새 지운 기사면 null
	 */
	record Representative(@Nullable ArticleId article, AdminArticles.@Nullable Summary summary) {

		static final Representative NONE = new Representative(null, null);

		@Nullable Long articleId() {
			return (this.article == null) ? null : this.article.value();
		}

		@Nullable ArticleSummaryResponse response() {
			return (this.summary == null) ? null : ArticleSummaryResponse.of(this.summary);
		}

	}

}
