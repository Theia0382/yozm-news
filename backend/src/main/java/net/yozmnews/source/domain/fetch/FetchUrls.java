package net.yozmnews.source.domain.fetch;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * 요청할 수 있는 주소와 리다이렉트 대상 — docs/specs/source.md §4 · source-R35.
 * <p>
 * 프로필 URL 검사 · 기사 링크 규칙과 지금은 조건이 비슷하지만 바뀌는 계기가 달라 따로 둔다.
 */
public final class FetchUrls {

	/** TCP 포트의 최댓값 */
	private static final int MAX_PORT = 65_535;

	private FetchUrls() {
	}

	/** 요청할 수 있는 주소인지 본다 — source-R35 */
	public static boolean isFetchable(URI uri) {
		String scheme = uri.getScheme();
		if (!uri.isAbsolute() || scheme == null || uri.getHost() == null || uri.getRawUserInfo() != null) {
			return false;
		}
		int port = uri.getPort();
		if (port != -1 && (port < 1 || port > MAX_PORT)) {
			return false;
		}
		String lower = scheme.toLowerCase(Locale.ROOT);
		return lower.equals("http") || lower.equals("https");
	}

	/**
	 * {@code Location} 을 현재 주소 기준으로 푼다 — 상대 참조 · 스킴 상대 참조를 포함한다. 앞뒤 공백은 지우고, 경로가 있는
	 * 참조는 결과 경로의 점 조각({@code .} · {@code ..})을 RFC 3986 §5.2.4 대로 지우고, 프래그먼트는 지운다. 요청할 수 있는
	 * 주소인지는 보지 않는다({@link #isFetchable}).
	 * @return 없거나 비었거나 URI 로 읽을 수 없으면 빈 값
	 */
	public static Optional<URI> resolveLocation(URI current, @Nullable String location) {
		if (location == null || location.isBlank()) {
			return Optional.empty();
		}
		URI reference;
		try {
			reference = new URI(location.strip());
		}
		catch (URISyntaxException ex) {
			return Optional.empty();
		}
		URI resolved = resolve(withPath(current), reference);
		String referencePath = reference.getRawPath();
		boolean hasPath = referencePath != null && !referencePath.isEmpty();
		return Optional.of(withoutFragment(hasPath ? withoutDotSegments(resolved) : resolved));
	}

	/**
	 * {@code URI.resolve} 는 기준 경로와 합친 경로의 점 조각만 지우고, 루트를 넘는 {@code ..}(예: {@code /../c})와 절대 경로
	 * 참조 안의 점 조각은 그대로 둔다. RFC 3986 §5.2.2 는 경로가 있는 참조의 결과 경로에서 점 조각을 모두 지운다.
	 */
	private static URI withoutDotSegments(URI uri) {
		String path = uri.getRawPath();
		if (uri.isOpaque() || uri.getRawAuthority() == null || path == null) {
			return uri;
		}
		String cleaned = removeDotSegments(path);
		if (cleaned.equals(path)) {
			return uri;
		}
		String query = uri.getRawQuery();
		String fragment = uri.getRawFragment();
		return URI.create(uri.getScheme() + "://" + uri.getRawAuthority() + cleaned
				+ ((query != null) ? "?" + query : "") + ((fragment != null) ? "#" + fragment : ""));
	}

	/** RFC 3986 §5.2.4 remove_dot_segments */
	private static String removeDotSegments(String path) {
		String input = path;
		StringBuilder output = new StringBuilder();
		while (!input.isEmpty()) {
			if (input.startsWith("../")) {
				input = input.substring(3);
			}
			else if (input.startsWith("./") || input.startsWith("/./")) {
				input = input.substring(2);
			}
			else if (input.equals("/.")) {
				input = "/";
			}
			else if (input.startsWith("/../") || input.equals("/..")) {
				input = "/" + input.substring(Math.min(4, input.length()));
				output.setLength(Math.max(output.lastIndexOf("/"), 0));
			}
			else if (input.equals(".") || input.equals("..")) {
				input = "";
			}
			else {
				int next = input.indexOf('/', 1);
				int end = (next < 0) ? input.length() : next;
				output.append(input, 0, end);
				input = input.substring(end);
			}
		}
		return output.toString();
	}

	/**
	 * {@code URI.resolve} 는 RFC 2396 을 따라 질의만 있는 참조({@code ?p=2})를 기준 경로의 마지막 조각을 지운 자리에 붙인다.
	 * RFC 3986 §5.2.2 는 기준 경로를 그대로 두고 질의만 바꾼다 — 그 경우만 따로 푼다.
	 */
	private static URI resolve(URI base, URI reference) {
		boolean queryOnly = reference.getScheme() == null && reference.getRawAuthority() == null
				&& reference.getRawPath() != null && reference.getRawPath().isEmpty() && reference.getRawQuery() != null;
		if (!queryOnly || base.isOpaque()) {
			return base.resolve(reference);
		}
		return URI.create(base.getScheme() + "://" + base.getRawAuthority() + base.getRawPath() + "?"
				+ reference.getRawQuery());
	}

	/** 경로가 빈 기준 URI({@code http://h})는 {@code URI.resolve} 가 상대 참조를 호스트에 붙이므로 경로를 {@code /} 로 둔다 */
	private static URI withPath(URI base) {
		String path = base.getRawPath();
		if (base.isOpaque() || (path != null && !path.isEmpty())) {
			return base;
		}
		String query = base.getRawQuery();
		return URI.create(base.getScheme() + "://" + base.getRawAuthority() + "/" + ((query != null) ? "?" + query : ""));
	}

	private static URI withoutFragment(URI uri) {
		if (uri.getRawFragment() == null) {
			return uri;
		}
		String text = uri.toString();
		return URI.create(text.substring(0, text.indexOf('#')));
	}

}
