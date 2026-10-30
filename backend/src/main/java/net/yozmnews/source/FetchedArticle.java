package net.yozmnews.source;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 정규화한 기사 하나 — docs/specs/source.md §3
 */
public record FetchedArticle(String title, URI link, @Nullable String guid, @Nullable String summary,
		Instant publishedAt, boolean publishedAtEstimated, @Nullable String author, List<String> categories,
		@Nullable URI thumbnail) {

	/** 요약문 상한 — docs/specs/source.md §3 */
	public static final int SUMMARY_MAX_CODE_POINTS = 1_000;

	public FetchedArticle {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(link, "link");
		Objects.requireNonNull(publishedAt, "publishedAt");
		categories = List.copyOf(categories);
		if (title.isBlank()) {
			throw new IllegalArgumentException("제목이 비어 있다");
		}
		if (!isWebUrl(link)) {
			throw new IllegalArgumentException("기사 링크는 http · https 웹 주소여야 한다: " + link);
		}
		if (thumbnail != null && !isWebUrl(thumbnail)) {
			throw new IllegalArgumentException("썸네일은 http · https 웹 주소여야 한다: " + thumbnail);
		}
		if (summary != null && summary.codePointCount(0, summary.length()) > SUMMARY_MAX_CODE_POINTS) {
			throw new IllegalArgumentException("요약문이 " + SUMMARY_MAX_CODE_POINTS + " 코드 포인트를 넘는다");
		}
	}

	/** 기사 링크 · 썸네일로 받는 주소인지 본다 — docs/specs/source.md §3 */
	public static boolean isWebUrl(URI uri) {
		String scheme = uri.getScheme();
		if (!uri.isAbsolute() || scheme == null || uri.getHost() == null) {
			return false;
		}
		String lower = scheme.toLowerCase(Locale.ROOT);
		return lower.equals("http") || lower.equals("https");
	}

}
