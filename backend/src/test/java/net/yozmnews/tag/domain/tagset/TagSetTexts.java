package net.yozmnews.tag.domain.tagset;

import static org.assertj.core.api.Assertions.assertThat;

/** 시험 도우미 — 형식 검증을 통과하는 원문 읽기 */
public final class TagSetTexts {

	private TagSetTexts() {
	}

	/** 원문을 읽고 형식 검증을 통과했는지 확인한 뒤 정의를 꺼낸다 */
	public static TagSetDefinition parsed(String text) {
		TagSetParse parse = new TagSetParser().parse(text);
		assertThat(parse).as("%s", parse).isInstanceOf(TagSetParse.Parsed.class);
		return ((TagSetParse.Parsed) parse).definition();
	}

}
