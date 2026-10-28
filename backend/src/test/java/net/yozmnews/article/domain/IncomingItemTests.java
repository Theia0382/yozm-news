package net.yozmnews.article.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import net.yozmnews.source.ArticleFetched;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.SourceId;
import org.junit.jupiter.api.Test;

/**
 * 들어온 항목의 판정 키 — 기대값은 손으로 적은 리터럴 · {@code sha256sum} 값이다.
 */
class IncomingItemTests {

	private static final Instant FETCHED_AT = Instant.parse("2026-10-28T01:00:00Z");

	@Test
	void computesKeysFromTheEvent() {
		IncomingItem item = IncomingItem.from(event("https://A.example/news/1?utm_source=rss#c", "g-1"));

		assertThat(item.sourceId()).isEqualTo(new SourceId(3));
		assertThat(item.feedId()).isEqualTo(new FeedId(5));
		assertThat(item.fetchedAt()).isEqualTo(FETCHED_AT);
		assertThat(item.canonicalUrl().value()).isEqualTo("https://a.example/news/1");
		assertThat(item.canonicalUrl().hash())
			.hasToString("0255a70a6060ad42610393d88c1de21f1f51cf6e6540fba7e8a5b1df21bd487f");
		assertThat(item.titleKey().value()).isEqualTo("국회본회의통과");
		assertThat(item.guidHash()).hasToString("b9817549cc546b0ee549a42a97b94811ed862005f8c17409217cc58128d8f630");
	}

	@Test
	void hasNoGuidHashWithoutAGuid() {
		assertThat(IncomingItem.from(event("https://a.example/news/1", null)).guidHash()).isNull();
	}

	private static ArticleFetched event(String link, String guid) {
		FetchedArticle article = new FetchedArticle("국회 본회의 통과", URI.create(link), guid, null,
				Instant.parse("2026-10-28T00:00:00Z"), false, null, List.of(), null);
		return new ArticleFetched(new SourceId(3), new FeedId(5), FETCHED_AT, article);
	}

}
