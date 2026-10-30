package net.yozmnews.source.domain.feed;

import java.util.Arrays;
import java.util.List;

import net.yozmnews.source.domain.profile.KnownNamespaces;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jspecify.annotations.Nullable;

/**
 * Rome 이 판별하는 피드 형식 — docs/specs/source.md §4 · source-R39.
 * <p>
 * 형식마다 항목 요소의 위치와 이름공간을 갖는다.
 */
enum FeedFormat {

	RSS_090("rss_0.9", Family.RDF, "http://my.netscape.com/rdf/simple/0.9/"),
	RSS_091N("rss_0.91N", Family.RSS, null),
	RSS_091U("rss_0.91U", Family.RSS, null),
	RSS_092("rss_0.92", Family.RSS, null),
	RSS_093("rss_0.93", Family.RSS, null),
	RSS_094("rss_0.94", Family.RSS, null),
	RSS_20("rss_2.0", Family.RSS, null),
	RSS_10("rss_1.0", Family.RDF, "http://purl.org/rss/1.0/"),
	ATOM_03("atom_0.3", Family.ATOM, "http://purl.org/atom/ns#"),
	ATOM_10("atom_1.0", Family.ATOM, KnownNamespaces.ATOM);

	/** 항목 요소를 찾는 방식과 표준 요소 표가 같은 형식 묶음 */
	enum Family {

		/** RSS 0.91 · 0.92 · 0.93 · 0.94 · 2.0 — {@code rss/channel/item} */
		RSS,
		/** RSS 0.90 · 1.0 — {@code rdf:RDF/item} */
		RDF,
		/** Atom 0.3 · 1.0 — {@code feed/entry} */
		ATOM

	}

	private final String romeType;

	private final Family family;

	/** 형식 이름공간 — null 이면 루트 요소의 이름공간(RSS 계열은 보통 없음이지만 기본 이름공간을 쓴 변형이 있다) */
	private final @Nullable String namespaceUri;

	FeedFormat(String romeType, Family family, @Nullable String namespaceUri) {
		this.romeType = romeType;
		this.family = family;
		this.namespaceUri = namespaceUri;
	}

	/**
	 * Rome 의 형식 이름으로 찾는다.
	 * @throws IllegalStateException Rome 이 이 목록 밖의 형식을 판별했을 때
	 */
	static FeedFormat ofRomeType(String type) {
		return Arrays.stream(values())
			.filter(format -> format.romeType.equals(type))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("모르는 Rome 피드 형식: " + type));
	}

	Family family() {
		return this.family;
	}

	/** 항목 요소와 형식 고유 요소의 이름공간을 돌려준다 */
	Namespace namespace(Document document) {
		return (this.namespaceUri != null) ? Namespace.getNamespace(this.namespaceUri)
				: document.getRootElement().getNamespace();
	}

	/** 항목 요소를 문서 순서로 돌려준다 */
	List<Element> itemElements(Document document) {
		Namespace namespace = namespace(document);
		Element root = document.getRootElement();
		return switch (this.family) {
			case RSS -> {
				Element channel = root.getChild("channel", namespace);
				yield (channel != null) ? channel.getChildren("item", namespace) : List.of();
			}
			case RDF -> root.getChildren("item", namespace);
			case ATOM -> root.getChildren("entry", namespace);
		};
	}

}
