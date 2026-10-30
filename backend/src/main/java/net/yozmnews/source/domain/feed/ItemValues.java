package net.yozmnews.source.domain.feed;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.source.domain.profile.ElementName;
import net.yozmnews.source.domain.profile.ItemField;
import net.yozmnews.source.domain.profile.ValueSource;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * 한 항목의 값 후보 수집 — 프로필의 읽을 곳 목록을 따른다
 */
final class ItemValues {

	/** 썸네일 요소가 주소를 담는 속성 — Media RSS · RSS enclosure 의 관례 */
	private static final String URL_ATTRIBUTE = "url";

	private ItemValues() {
	}

	/**
	 * 한 항목의 값 후보를 모은다.
	 * @return 읽을 곳마다의 원문 값 — 바깥 목록은 읽을 곳 순서, 안쪽 목록은 문서 순서
	 */
	static List<List<String>> candidates(Element item, FeedFormat format, ItemField field, List<ValueSource> sources) {
		List<List<String>> candidates = new ArrayList<>(sources.size());
		for (ValueSource source : sources) {
			candidates.add(switch (source) {
				case ValueSource.Standard standard -> StandardElements.values(item, format, field);
				case ValueSource.Element element -> elementValues(item, element.name(), field);
			});
		}
		return candidates;
	}

	/** 항목의 직계 자식 중 그 이름의 요소들의 값을 읽는다 — 값은 텍스트, 썸네일은 {@code url} 속성이 있으면 그 값 */
	private static List<String> elementValues(Element item, ElementName name, ItemField field) {
		Namespace namespace = Namespace.getNamespace(name.namespaceUri());
		return item.getChildren(name.localName(), namespace).stream().map(element -> {
			String url = (field == ItemField.THUMBNAIL) ? element.getAttributeValue(URL_ATTRIBUTE) : null;
			return (url != null) ? url : element.getValue();
		}).toList();
	}

}
