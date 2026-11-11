package net.yozmnews.admin.adapter.in.web;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleEntryView;
import net.yozmnews.article.ArticleId;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.FeedLabels;
import org.jspecify.annotations.Nullable;

/**
 * 운영자 응답의 기사 정보 맞붙이기(평가 검수 · 사건 · 큰 스토리라인 · 판정 기록) — 정본이다 · docs/specs/admin.md §5 admin-R6.
 * <p>
 * 기사 ID 를 모아 {@code article} 의 {@link ArticleDirectory#findAll} 을 한 번, 그 엔트리의 피드 ID 를 모아 {@code source} 의
 * {@link FeedLabels#labels} 를 한 번 부른다. 그새 지운 기사는 결과에 없다 — 응답의 기사 정보가 {@code null} 이다. 요약문 원문은 운영자 화면에만
 * 보인다(ADR-0068).
 */
final class AdminArticles {

	private final ArticleDirectory articles;

	private final FeedLabels labels;

	AdminArticles(ArticleDirectory articles, FeedLabels labels) {
		this.articles = Objects.requireNonNull(articles, "articles");
		this.labels = Objects.requireNonNull(labels, "labels");
	}

	/** 목록 · 표본 행의 기사 — 제목과 출처 이름(엔트리 순서, 같은 이름은 한 번, 이름이 없는 피드는 뺀다) */
	Map<ArticleId, Summary> summaries(Collection<ArticleId> ids) {
		Map<ArticleId, ArticleContent> contents = this.articles.findAll(ids);
		Map<FeedId, FeedLabel> labels = this.labels.labels(feeds(contents.values()));
		Map<ArticleId, Summary> summaries = new HashMap<>();
		contents.forEach((id, content) -> {
			Set<String> sources = new LinkedHashSet<>();
			for (ArticleEntryView entry : content.entries()) {
				FeedLabel label = labels.get(entry.feedId());
				if (label != null && label.sourceName() != null) {
					sources.add(label.sourceName());
				}
			}
			summaries.put(id, new Summary(content.title(), List.copyOf(sources)));
		});
		return summaries;
	}

	/** 상세의 기사 — 제목 · 요약문 · 정규 URL 과 엔트리마다 출처 이름 · 섹션 · RSS 카테고리 */
	Optional<Detail> detail(ArticleId id) {
		ArticleContent content = this.articles.findAll(List.of(id)).get(id);
		if (content == null) {
			return Optional.empty();
		}
		Map<FeedId, FeedLabel> labels = this.labels.labels(feeds(List.of(content)));
		List<Entry> entries = content.entries().stream().map(entry -> {
			FeedLabel label = labels.get(entry.feedId());
			return new Entry((label == null) ? null : label.sourceName(), (label == null) ? null : label.section(),
					entry.categories());
		}).toList();
		return Optional.of(new Detail(content.title(), content.summary(), content.canonicalUrl(), entries));
	}

	private static Set<FeedId> feeds(Collection<ArticleContent> contents) {
		Set<FeedId> feeds = new LinkedHashSet<>();
		contents.forEach(content -> content.entries().forEach(entry -> feeds.add(entry.feedId())));
		return feeds;
	}

	/**
	 * 목록 · 표본 행의 기사.
	 * @param sources 출처 이름 — 엔트리 순서, 같은 이름은 한 번
	 */
	record Summary(String title, List<String> sources) {
	}

	/**
	 * 상세의 기사.
	 * @param summary 저장된 RSS 요약문 — 없으면 null
	 * @param canonicalUrl 원문 링크(정규 URL)
	 * @param entries 엔트리마다 — 받은 시각 순
	 */
	record Detail(String title, @Nullable String summary, URI canonicalUrl, List<Entry> entries) {
	}

	/**
	 * 엔트리 하나.
	 * @param sourceName 출처 이름 — 없으면 null
	 * @param section 피드 섹션 — 없으면 null
	 * @param categories 그 피드에서 처음 본 항목의 RSS 카테고리
	 */
	record Entry(@Nullable String sourceName, @Nullable String section, List<String> categories) {
	}

}
