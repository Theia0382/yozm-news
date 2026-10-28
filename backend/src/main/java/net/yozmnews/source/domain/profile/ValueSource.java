package net.yozmnews.source.domain.profile;

import java.util.Objects;

/**
 * 기사 항목 값을 읽을 곳 하나. 프로필 텍스트에서는 {@code $standard} 또는 요소 이름({@code dc:date} · {@code author})이다.
 */
public sealed interface ValueSource {

	/** 프로필 텍스트에서 파서 표준값을 가리키는 표기 — {@code $} 는 XML 이름에 쓸 수 없어 요소 이름과 겹치지 않는다 */
	String STANDARD_TOKEN = "$standard";

	/** 그 피드 형식의 명세가 정한 요소의 값(RSS 2.0 의 {@code pubDate} · Atom 의 {@code published} 등) */
	record Standard() implements ValueSource {

	}

	/** 지정한 요소의 값 */
	record Element(ElementName name) implements ValueSource {

		public Element {
			Objects.requireNonNull(name, "name");
		}

	}

}
