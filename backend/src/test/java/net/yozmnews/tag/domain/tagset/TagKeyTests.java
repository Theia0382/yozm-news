package net.yozmnews.tag.domain.tagset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 태그 키 형식 — 키 판정 · 중분류 판정 · 마지막 조각을 리터럴로 본다.
 * <p>
 * 지키는 규칙: tag-R6
 */
class TagKeyTests {

	@Test
	void tellsKeysFromOtherText() {
		assertThat(TagKey.isKey("politics")).isTrue();
		assertThat(TagKey.isKey("politics.executive")).isTrue();
		assertThat(TagKey.isKey("a.b.c")).isFalse();
		assertThat(TagKey.isKey("politics.")).isFalse();
		assertThat(TagKey.isKey(".executive")).isFalse();
		assertThat(TagKey.isKey("Politics")).isFalse();
		assertThat(TagKey.isKey("")).isFalse();
	}

	@Test
	void tellsSubtopicKeysAndTheirLastSegment() {
		assertThat(new TagKey("politics.executive").isChild()).isTrue();
		assertThat(new TagKey("politics").isChild()).isFalse();
		assertThat(new TagKey("politics.executive").leaf()).isEqualTo("executive");
		assertThat(new TagKey("politics").leaf()).isEqualTo("politics");
	}

}
