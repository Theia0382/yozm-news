package net.yozmnews.source;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 정규화된 기사 하나 — 피드 항목을 수집 프로필에 따라 읽고 보정한 값. 원문 본문은 담지 않는다.
 *
 * @param title 정리한 제목 — 비어 있지 않다
 * @param link 피드가 준 기사 링크 — {@link #isWebUrl} 을 만족한다. 같은 기사 판정용 정규 URL 은 article 이 만든다
 * @param guid 피드의 항목 식별자 원문(RSS guid · RSS 1.0 rdf:about · Atom id, 앞뒤 공백 제거) — 없을 수 있다
 * @param summary 정리한 요약문 — 최대 {@link #SUMMARY_MAX_CODE_POINTS} 코드 포인트, 없을 수 있다
 * @param publishedAt 발행 시각
 * @param publishedAtEstimated 발행 시각을 해석하지 못해 받은 시각으로 채웠는지
 * @param author 기자명 — 없을 수 있다
 * @param categories 피드 카테고리 — 피드 순서, 중복 없음, 비어 있을 수 있다
 * @param thumbnail 썸네일 — {@link #isWebUrl} 을 만족한다, 없을 수 있다
 */
public record FetchedArticle(String title, URI link, @Nullable String guid, @Nullable String summary,
		Instant publishedAt, boolean publishedAtEstimated, @Nullable String author, List<String> categories,
		@Nullable URI thumbnail) {

	/** 요약문 상한 — 본문 전체를 담은 요약문도 이 길이에서 자른다 */
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

	/** 기사 링크 · 썸네일로 받는 주소인가 — 절대 URI, 스킴 http · https(대소문자 무시), 호스트 있음 */
	public static boolean isWebUrl(URI uri) {
		String scheme = uri.getScheme();
		if (!uri.isAbsolute() || scheme == null || uri.getHost() == null) {
			return false;
		}
		String lower = scheme.toLowerCase(Locale.ROOT);
		return lower.equals("http") || lower.equals("https");
	}

}
