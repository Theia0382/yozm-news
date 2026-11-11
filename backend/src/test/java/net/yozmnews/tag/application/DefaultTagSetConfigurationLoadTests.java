package net.yozmnews.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import net.yozmnews.tag.domain.tagset.TagSetDefinition;
import net.yozmnews.tag.domain.tagset.TagSetParser;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 기본 체계 파일의 기동 검증 — 테스트 리소스의 합성 파일로 본다. Spring 컨텍스트는 띄우지 않는다.
 * <p>
 * 지키는 규칙: tag-R1
 */
class DefaultTagSetConfigurationLoadTests {

	private final TagSetParser parser = new TagSetParser();

	@Test
	void loadsAValidFile() {
		TagSetDefinition definition = DefaultTagSetConfiguration.load(new ClassPathResource("tag-sets-test/valid.yaml"),
				this.parser);

		assertThat(definition.content().topics()).extracting(topic -> topic.key().value()).containsExactly("alpha");
		assertThat(definition.content().topics().get(0).subtopics()).extracting(tag -> tag.key().value())
			.containsExactly("alpha.one");
	}

	@Test
	void failsWithEveryProblemOfABrokenFile() {
		assertThatIllegalStateException()
			.isThrownBy(() -> DefaultTagSetConfiguration.load(new ClassPathResource("tag-sets-test/broken.yaml"),
					this.parser))
			.withMessageContaining("broken.yaml")
			.withMessageContaining("11:10 topics[0].key")
			.withMessageContaining("[INVALID_KEY]")
			.withMessageContaining("16:10 regions")
			.withMessageContaining("[NO_REGIONS]");
	}

	@Test
	void failsWhenTheFileHasALineageSection() {
		assertThatIllegalStateException()
			.isThrownBy(() -> DefaultTagSetConfiguration.load(new ClassPathResource("tag-sets-test/with-lineage.yaml"),
					this.parser))
			.withMessageContaining("with-lineage.yaml")
			.withMessageContaining("28:5 lineage: 기본 체계 파일에는 계보를 둘 수 없다 [LINEAGE_IN_DEFAULT]");
	}

	@Test
	void failsWhenTheFileIsMissing() {
		assertThatIllegalStateException()
			.isThrownBy(() -> DefaultTagSetConfiguration.load(new ClassPathResource("tag-sets-test/missing.yaml"),
					this.parser))
			.withMessageContaining("기본 태그 체계 파일이 없다")
			.withMessageContaining("missing.yaml");
	}

}
