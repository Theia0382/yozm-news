package net.yozmnews.source.domain.feed;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.yozmnews.source.domain.profile.ItemField;
import net.yozmnews.source.domain.profile.KnownNamespaces;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jspecify.annotations.Nullable;

/**
 * 프로필의 {@code $standard} 가 읽는 요소 — docs/specs/source.md §4 · source-R40 · source-R41.
 * <p>
 * RSS 0.91~0.94 는 판을 구별하지 않고 RSS 2.0 의 이름으로 읽는다(없는 요소는 값이 없을 뿐이다).
 */
final class StandardElements {

	private static final Namespace DUBLIN_CORE = Namespace.getNamespace(KnownNamespaces.DUBLIN_CORE);

	private static final Namespace RDF = Namespace.getNamespace("http://www.w3.org/1999/02/22-rdf-syntax-ns#");

	private static final String IMAGE_TYPE = "image/";

	private StandardElements() {
	}

	/** 항목의 원문 값을 문서 순서로 읽는다 — 정리 전 */
	static List<String> values(Element item, FeedFormat format, ItemField field) {
		Namespace namespace = format.namespace(item.getDocument());
		return switch (field) {
			case PUBLISHED_AT -> switch (format.family()) {
				case RSS -> texts(item, "pubDate", namespace);
				case RDF -> texts(item, "date", DUBLIN_CORE);
				case ATOM -> texts(item, (format == FeedFormat.ATOM_03) ? "issued" : "published", namespace);
			};
			case SUMMARY -> switch (format.family()) {
				case RSS, RDF -> texts(item, "description", namespace);
				case ATOM -> texts(item, "summary", namespace);
			};
			case AUTHOR -> switch (format.family()) {
				case RSS -> texts(item, "author", namespace);
				case RDF -> texts(item, "creator", DUBLIN_CORE);
				case ATOM -> item.getChildren("author", namespace)
					.stream()
					.map(author -> author.getChild("name", namespace))
					.filter(Objects::nonNull)
					.map(Element::getValue)
					.toList();
			};
			case CATEGORY -> switch (format.family()) {
				case RSS -> texts(item, "category", namespace);
				case RDF -> texts(item, "subject", DUBLIN_CORE);
				case ATOM -> attributes(item.getChildren("category", namespace), "term");
			};
			case THUMBNAIL -> switch (format.family()) {
				case RSS -> attributes(images(item.getChildren("enclosure", namespace)), "url");
				case RDF -> List.of();
				case ATOM -> attributes(images(item.getChildren("link", namespace)
					.stream()
					.filter(link -> "enclosure".equals(link.getAttributeValue("rel")))
					.toList()), "href");
			};
		};
	}

	/**
	 * 항목 식별자 원문을 읽는다 — RSS {@code guid} · RDF {@code rdf:about} · Atom {@code id}.
	 * @return 없으면 null
	 */
	static @Nullable String guid(Element item, FeedFormat format) {
		Namespace namespace = format.namespace(item.getDocument());
		return switch (format.family()) {
			case RSS -> item.getChildText("guid", namespace);
			case RDF -> item.getAttributeValue("about", RDF);
			case ATOM -> item.getChildText("id", namespace);
		};
	}

	private static List<String> texts(Element item, String name, Namespace namespace) {
		return item.getChildren(name, namespace).stream().map(Element::getValue).toList();
	}

	private static List<String> attributes(List<Element> elements, String attribute) {
		return elements.stream().map(element -> element.getAttributeValue(attribute)).filter(Objects::nonNull).toList();
	}

	/** {@code type} 이 이미지인 요소를 고른다 */
	private static List<Element> images(List<Element> elements) {
		return elements.stream().filter(element -> {
			String type = element.getAttributeValue("type");
			return type != null && type.strip().toLowerCase(Locale.ROOT).startsWith(IMAGE_TYPE);
		}).toList();
	}

}
