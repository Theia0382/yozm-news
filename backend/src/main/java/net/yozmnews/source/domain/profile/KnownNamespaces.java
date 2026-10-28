package net.yozmnews.source.domain.profile;

import java.util.Map;

/**
 * 프로필이 선언하지 않아도 쓸 수 있는 이름공간 접두사와 그 URI. 피드 정규화도 같은 URI 를 쓴다.
 */
public final class KnownNamespaces {

	/** Dublin Core — {@code dc} */
	public static final String DUBLIN_CORE = "http://purl.org/dc/elements/1.1/";

	/** RSS 1.0 content 모듈 — {@code content} */
	public static final String CONTENT = "http://purl.org/rss/1.0/modules/content/";

	/** Media RSS — {@code media} */
	public static final String MEDIA = "http://search.yahoo.com/mrss/";

	/** Atom 1.0 — {@code atom} */
	public static final String ATOM = "http://www.w3.org/2005/Atom";

	static final Map<String, String> PREFIXES = Map.of("dc", DUBLIN_CORE, "content", CONTENT, "media", MEDIA, "atom",
			ATOM);

	private KnownNamespaces() {
	}

}
