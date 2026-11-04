package net.yozmnews.source.domain.poll;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import net.yozmnews.source.FetchedArticle;
import org.jspecify.annotations.Nullable;

/** 수집 일정 테스트의 기사 · 키 */
final class PollFixtures {

	static final Instant PUBLISHED = Instant.parse("2026-10-28T00:00:00Z");

	private PollFixtures() {
	}

	/** guid 가 있는 기사 — 링크는 guid 에서 만든다 */
	static FetchedArticle article(String guid) {
		return article(guid, "https://news.example/" + guid);
	}

	static FetchedArticle article(@Nullable String guid, String link) {
		return new FetchedArticle("제목 " + link, URI.create(link), guid, null, PUBLISHED, false, null, List.of(), null);
	}

	/** guid 가 있는 기사 — 발행 시각을 정한다. 추정이면 그 시각은 받은 시각이다(source-R40) */
	static FetchedArticle article(String guid, Instant publishedAt, boolean publishedAtEstimated) {
		return new FetchedArticle("제목 " + guid, URI.create("https://news.example/" + guid), guid, null, publishedAt,
				publishedAtEstimated, null, List.of(), null);
	}

	static FetchedArticle linkOnly(String link) {
		return article(null, link);
	}

	static List<ItemKey> keys(String... values) {
		return java.util.Arrays.stream(values).map(ItemKey::new).toList();
	}

	/** guid 가 {@code prefix1} … {@code prefix{count}} 인 기사 — 문서 순서 */
	static List<FetchedArticle> articles(String prefix, int count) {
		return IntStream.rangeClosed(1, count).mapToObj(i -> article(prefix + i)).toList();
	}

	/** {@code g:prefix1} … {@code g:prefix{count}} 키 */
	static List<ItemKey> keysOf(String prefix, int count) {
		return IntStream.rangeClosed(1, count).mapToObj(i -> new ItemKey("g:" + prefix + i)).toList();
	}

}
