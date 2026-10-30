package net.yozmnews.article.domain;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.source.FetchedArticle;
import org.jspecify.annotations.Nullable;

/**
 * 정규 URL — docs/specs/article.md §4 · article-R1
 */
public record CanonicalUrl(String value) {

	/** 추적 파라미터 이름의 접두사 */
	static final List<String> TRACKING_PREFIXES = List.of("utm_");

	/** 추적 파라미터 이름 */
	static final Set<String> TRACKING_NAMES = Set.of("fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid",
			"yclid", "igshid", "mc_cid", "mc_eid");

	public CanonicalUrl {
		Objects.requireNonNull(value, "value");
	}

	/**
	 * 기사 링크의 정규 URL 을 만든다. 원 URI 의 raw 구성 요소로 조립해 퍼센트 인코딩을 바꾸지 않는다.
	 * @throws IllegalArgumentException 링크가 {@link FetchedArticle#isWebUrl} 을 만족하지 않을 때
	 */
	public static CanonicalUrl of(URI link) {
		if (!FetchedArticle.isWebUrl(link)) {
			throw new IllegalArgumentException("기사 링크는 http · https 웹 주소여야 한다: " + link);
		}
		String scheme = link.getScheme().toLowerCase(Locale.ROOT);
		StringBuilder url = new StringBuilder(scheme).append("://");
		String userInfo = link.getRawUserInfo();
		if (userInfo != null) {
			url.append(userInfo).append('@');
		}
		url.append(link.getHost().toLowerCase(Locale.ROOT));
		int port = link.getPort();
		if (port != -1 && port != defaultPort(scheme)) {
			url.append(':').append(port);
		}
		String path = link.getRawPath();
		url.append((path == null || path.isEmpty()) ? "/" : path);
		String query = withoutTracking(link.getRawQuery());
		if (!query.isEmpty()) {
			url.append('?').append(query);
		}
		return new CanonicalUrl(url.toString());
	}

	public KeyHash hash() {
		return KeyHash.of(this.value);
	}

	private static int defaultPort(String scheme) {
		return scheme.equals("https") ? 443 : 80;
	}

	private static String withoutTracking(@Nullable String rawQuery) {
		if (rawQuery == null) {
			return "";
		}
		List<String> kept = new ArrayList<>();
		for (String piece : rawQuery.split("&", -1)) {
			if (!isTracking(piece)) {
				kept.add(piece);
			}
		}
		return String.join("&", kept);
	}

	private static boolean isTracking(String piece) {
		int equals = piece.indexOf('=');
		String name = ((equals < 0) ? piece : piece.substring(0, equals)).toLowerCase(Locale.ROOT);
		return TRACKING_NAMES.contains(name) || TRACKING_PREFIXES.stream().anyMatch(name::startsWith);
	}

}
