package net.yozmnews.article.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleEntryView;
import net.yozmnews.article.ArticleHeadline;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.domain.ArticleRepository;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.FeedLabels;
import net.yozmnews.source.SourceId;
import org.junit.jupiter.api.Test;

/**
 * 헤드라인 — 내용 한 번 · 피드 이름 한 번으로 읽고, 요약문을 담지 않으며, 지운 기사는 빠진다.
 * <p>
 * 지키는 규칙: article-R17
 */
class ArticleDirectoryServiceTests {

	private static final Instant PUBLISHED = Instant.parse("2026-10-28T00:00:00Z");

	private final Map<ArticleId, ArticleContent> stored = new LinkedHashMap<>();

	private final List<Collection<ArticleId>> contentCalls = new ArrayList<>();

	private final List<Collection<FeedId>> labelCalls = new ArrayList<>();

	@Test
	void headlinesCarryTheTitleTheNamedSourcesThePublishedTimeAndTheLinkButNoSummary() {
		this.stored.put(new ArticleId(41), new ArticleContent(new ArticleId(41), "국회 본회의 통과", "요약문 원문",
				URI.create("https://news.example/41"), PUBLISHED,
				List.of(entry(7), entry(5), entry(6))));
		this.stored.put(new ArticleId(42), new ArticleContent(new ArticleId(42), "기준금리 동결", null,
				URI.create("https://news.example/42"), Instant.parse("2026-10-27T09:15:00Z"), List.of(entry(5))));
		ArticleDirectoryService service = service(Map.of(new FeedId(5), new FeedLabel("경향신문", "경제"),
				new FeedId(6), new FeedLabel(null, null), new FeedId(7), new FeedLabel("한겨레", "정치")));

		Map<ArticleId, ArticleHeadline> headlines = service
			.headlines(List.of(new ArticleId(42), new ArticleId(41), new ArticleId(99)));

		assertThat(headlines).containsOnlyKeys(new ArticleId(41), new ArticleId(42));
		assertThat(headlines.get(new ArticleId(41))).isEqualTo(new ArticleHeadline(new ArticleId(41), "국회 본회의 통과",
				List.of("한겨레", "경향신문"), PUBLISHED, URI.create("https://news.example/41")));
		assertThat(headlines.get(new ArticleId(42))).isEqualTo(new ArticleHeadline(new ArticleId(42), "기준금리 동결",
				List.of("경향신문"), Instant.parse("2026-10-27T09:15:00Z"), URI.create("https://news.example/42")));
		// 내용 한 번, 피드 이름 한 번 — 피드는 내용 결과 순서(42 의 5, 41 의 7 · 5 · 6)에서 처음 나온 순서로 한 번씩
		assertThat(this.contentCalls).containsExactly(List.of(new ArticleId(42), new ArticleId(41), new ArticleId(99)));
		assertThat(this.labelCalls).containsExactly(List.of(new FeedId(5), new FeedId(7), new FeedId(6)));
	}

	@Test
	void noArticlesMeansNoQueries() {
		ArticleDirectoryService service = service(Map.of());

		assertThat(service.headlines(List.of())).isEmpty();
		assertThat(service.headlines(List.of(new ArticleId(99)))).isEmpty();
		// 빈 입력은 내용 조회도 하지 않고, 찾은 기사가 없으면 피드 이름 조회를 하지 않는다
		assertThat(this.contentCalls).containsExactly(List.of(new ArticleId(99)));
		assertThat(this.labelCalls).isEmpty();
	}

	private ArticleDirectoryService service(Map<FeedId, FeedLabel> labels) {
		ArticleRepository repository = (ArticleRepository) Proxy.newProxyInstance(
				ArticleRepository.class.getClassLoader(), new Class<?>[] { ArticleRepository.class },
				(proxy, method, args) -> {
					if (!method.getName().equals("findContents")) {
						throw new UnsupportedOperationException(method.getName());
					}
					@SuppressWarnings("unchecked")
					Collection<ArticleId> ids = (Collection<ArticleId>) args[0];
					this.contentCalls.add(List.copyOf(ids));
					Map<ArticleId, ArticleContent> found = new LinkedHashMap<>();
					ids.forEach(id -> {
						ArticleContent content = this.stored.get(id);
						if (content != null) {
							found.put(id, content);
						}
					});
					return found;
				});
		FeedLabels feedLabels = feeds -> {
			this.labelCalls.add(List.copyOf(feeds));
			Map<FeedId, FeedLabel> found = new LinkedHashMap<>();
			feeds.forEach(feed -> {
				FeedLabel label = labels.get(feed);
				if (label != null) {
					found.put(feed, label);
				}
			});
			return found;
		};
		return new ArticleDirectoryService(repository, feedLabels);
	}

	private static ArticleEntryView entry(long feed) {
		return new ArticleEntryView(new SourceId(3), new FeedId(feed), List.of());
	}

}
