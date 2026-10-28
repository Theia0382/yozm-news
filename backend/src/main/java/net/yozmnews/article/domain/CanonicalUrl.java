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
 * 같은 기사 판정용 정규 URL — {@code canonical_url} 이고, 화면의 원문 링크로도 쓴다. 자원을 바꾸지 않는 변환만 한다: 스킴
 * · 호스트 소문자, 기본 포트(http 80 · https 443) 제거, 빈 경로는 {@code /}, 프래그먼트 제거, 추적 파라미터 제거, 남은
 * 질의가 비면 {@code ?} 도 없앤다. 경로와 남은 파라미터의 순서 · 값 · 인코딩은 그대로 둔다 — 서버가 순서 · 대소문자를
 * 구분할 수 있다.
 *
 * @param value 정규 URL
 */
public record CanonicalUrl(String value) {

	/** 추적 파라미터 이름의 접두사 — 대소문자 무시 */
	static final List<String> TRACKING_PREFIXES = List.of("utm_");

	/** 추적 파라미터 이름 — 대소문자 무시 */
	static final Set<String> TRACKING_NAMES = Set.of("fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid",
			"yclid", "igshid", "mc_cid", "mc_eid");

	public CanonicalUrl {
		Objects.requireNonNull(value, "value");
	}

	/**
	 * 기사 링크의 정규 URL. 조립은 원 URI 의 raw 구성요소로 해서 퍼센트 인코딩이 바뀌지 않는다.
	 * @param link {@link FetchedArticle#isWebUrl} 을 만족하는 주소 — 아니면 거부한다
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

	/** 추적 파라미터 조각만 지우고, 남은 조각은 원문 그대로 원래 순서로 다시 잇는다 — 빈 조각도 남긴다 */
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
