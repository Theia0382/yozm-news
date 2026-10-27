package net.yozmnews.source.domain.profile;

import java.util.Map;

/**
 * 프로필이 선언하지 않아도 쓸 수 있는 이름공간 접두사.
 */
final class KnownNamespaces {

	static final Map<String, String> PREFIXES = Map.of("dc", "http://purl.org/dc/elements/1.1/", "content",
			"http://purl.org/rss/1.0/modules/content/", "media", "http://search.yahoo.com/mrss/", "atom",
			"http://www.w3.org/2005/Atom");

	private KnownNamespaces() {
	}

}
