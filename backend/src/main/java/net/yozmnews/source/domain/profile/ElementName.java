package net.yozmnews.source.domain.profile;

import java.util.Objects;

/**
 * 피드 항목 안의 XML 요소 이름 — 접두사가 아니라 이름공간 URI 로 정규화한 것이다.
 *
 * @param namespaceUri 이름공간 URI — 접두사 없이 쓴 이름이면 빈 문자열
 * @param localName 이름공간 안의 이름
 */
public record ElementName(String namespaceUri, String localName) {

	public ElementName {
		Objects.requireNonNull(namespaceUri, "namespaceUri");
		Objects.requireNonNull(localName, "localName");
	}

}
