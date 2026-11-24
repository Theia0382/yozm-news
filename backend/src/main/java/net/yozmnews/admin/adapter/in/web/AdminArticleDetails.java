package net.yozmnews.admin.adapter.in.web;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.FeedLabels;
import org.jspecify.annotations.Nullable;

/**
 * 운영자 응답의 기사 상세 맞붙이기(평가 상세 · 기사 상세) — 정본이다 · docs/specs/admin.md §5 admin-R6.
 * <p>
 * 기사 하나의 {@code article} 의 {@link ArticleDirectory#findAll} 과 그 엔트리의 피드 ID 로 {@code source} 의 {@link FeedLabels#labels} 를
 * 한 번씩 부른다 — 엔트리마다 출처 이름 · 섹션 · RSS 카테고리를 따로 싣는다(엮지 않는다). 그새 지운 기사는 빈 값이다. 요약문 원문은 운영자 화면에만
 * 보인다(ADR-0068). 목록 · 표본 행의 기사는 {@link AdminArticles} 가 낸다.
 */
final class AdminArticleDetails {

	private final ArticleDirectory articles;

	private final FeedLabels labels;

	AdminArticleDetails(ArticleDirectory articles, FeedLabels labels) {
		this.articles = Objects.requireNonNull(articles, "articles");
		this.labels = Objects.requireNonNull(labels, "labels");
	}

	/** 상세의 기사 — 제목 · 요약문 · 정규 URL · 발행 시각과 엔트리마다 출처 이름 · 섹션 · RSS 카테고리 */
	Optional<Detail> detail(ArticleId id) {
		ArticleContent content = this.articles.findAll(List.of(id)).get(id);
		if (content == null) {
			return Optional.empty();
		}
		Set<FeedId> feeds = new LinkedHashSet<>();
		content.entries().forEach(entry -> feeds.add(entry.feedId()));
		Map<FeedId, FeedLabel> labels = this.labels.labels(feeds);
		List<Entry> entries = content.entries().stream().map(entry -> {
			FeedLabel label = labels.get(entry.feedId());
			return new Entry((label == null) ? null : label.sourceName(), (label == null) ? null : label.section(),
					entry.categories());
		}).toList();
		return Optional.of(new Detail(content.title(), content.summary(), content.canonicalUrl(),
				content.publishedAt(), entries));
	}

	/**
	 * 상세의 기사.
	 * @param summary 저장된 RSS 요약문 — 없으면 null
	 * @param canonicalUrl 원문 링크(정규 URL)
	 * @param publishedAt 기사의 발행 시각(article-R8)
	 * @param entries 엔트리마다 — 받은 시각 순
	 */
	record Detail(String title, @Nullable String summary, URI canonicalUrl, Instant publishedAt, List<Entry> entries) {
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
