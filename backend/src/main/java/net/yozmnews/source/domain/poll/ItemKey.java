package net.yozmnews.source.domain.poll;

import java.util.Objects;

import net.yozmnews.source.FetchedArticle;

/**
 * 같은 피드 안에서 항목을 가르는 키 — guid 가 있으면 {@code g:} + guid, 없으면 {@code l:} + 링크 원문. 접두가 두 이름공간을
 * 가르므로, 한 항목의 guid 가 다른 항목의 링크와 같아도 키가 다르다. 같은 기사 판정(정규 URL)은 article 의 일이라 여기서는 원문을
 * 그대로 쓴다.
 *
 * @param value 접두를 붙인 값
 */
public record ItemKey(String value) {

	private static final String GUID_PREFIX = "g:";

	private static final String LINK_PREFIX = "l:";

	public ItemKey {
		Objects.requireNonNull(value, "value");
		if (!value.startsWith(GUID_PREFIX) && !value.startsWith(LINK_PREFIX)) {
			throw new IllegalArgumentException("항목 키는 g: 나 l: 로 시작한다: " + value);
		}
	}

	public static ItemKey of(FetchedArticle article) {
		String guid = article.guid();
		return new ItemKey((guid != null) ? GUID_PREFIX + guid : LINK_PREFIX + article.link());
	}

}
