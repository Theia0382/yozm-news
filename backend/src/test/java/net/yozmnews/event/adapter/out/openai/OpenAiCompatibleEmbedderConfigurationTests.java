package net.yozmnews.event.adapter.out.openai;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.event.domain.feature.ArticleEmbedder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * OpenAI 호환 외부 임베딩의 결선 — 필수 값 · 범위 · 형식이 틀리면 기동하지 않고 문구에 설정 이름은 있되 키 값은 없음을 본다. 설정 레코드의
 * {@code toString} 이 키를 숨기는지도 본다.
 * <p>
 * 지키는 규칙: event-R7 · docs/security.md(비밀 값이 문구에 남지 않는다)
 */
class OpenAiCompatibleEmbedderConfigurationTests {

	private static final String KEY = "sk-configuration-secret-0123";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web,worker",
				"yozmnews.embedding.provider=openai-compatible",
				"yozmnews.embedding.openai-compatible.base-url=https://api.example.test/v1",
				"yozmnews.embedding.openai-compatible.model=m-1",
				"yozmnews.embedding.openai-compatible.dimensions=256",
				"yozmnews.embedding.openai-compatible.api-key=" + KEY)
		.withUserConfiguration(OpenAiCompatibleEmbedderConfiguration.class);

	@Test
	void registersTheEmbedderWithValidSettings() {
		this.runner.run(context -> {
			assertThat(context).hasSingleBean(ArticleEmbedder.class);
			assertThat(context.getBean(ArticleEmbedder.class)).isInstanceOf(OpenAiCompatibleEmbedder.class);
			assertThat(context.getBean(OpenAiCompatibleProperties.class).toString()).contains("apiKey=숨김")
				.doesNotContain(KEY);
		});
	}

	@Test
	void refusesMissingOrInvalidSettingsNamingTheSettingWithoutTheKey() {
		assertFails("yozmnews.embedding.openai-compatible.base-url=", "yozmnews.embedding.openai-compatible.base-url",
				"YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_BASE_URL");
		assertFails("yozmnews.embedding.openai-compatible.base-url=https://api.example.test/v1?q=1",
				"yozmnews.embedding.openai-compatible.base-url", "YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_BASE_URL");
		assertFails("yozmnews.embedding.openai-compatible.base-url=ftp://api.example.test/v1",
				"yozmnews.embedding.openai-compatible.base-url", "YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_BASE_URL");
		assertFails("yozmnews.embedding.openai-compatible.model= ", "yozmnews.embedding.openai-compatible.model",
				"YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_MODEL");
		assertFails("yozmnews.embedding.openai-compatible.dimensions=0", "yozmnews.embedding.openai-compatible.dimensions",
				"YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_DIMENSIONS");
		assertFails("yozmnews.embedding.openai-compatible.dimensions=4001",
				"yozmnews.embedding.openai-compatible.dimensions", "YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_DIMENSIONS");
		assertFails("yozmnews.embedding.openai-compatible.batch-size=0", "yozmnews.embedding.openai-compatible.batch-size",
				"YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_BATCH_SIZE");
		assertFails("yozmnews.embedding.openai-compatible.batch-size=257",
				"yozmnews.embedding.openai-compatible.batch-size", "YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_BATCH_SIZE");
		assertFails("yozmnews.embedding.openai-compatible.api-key=" + KEY + " 공백",
				"yozmnews.embedding.openai-compatible.api-key", "YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_API_KEY");
	}

	@Test
	void acceptsTheBoundariesAndNoKey() {
		this.runner
			.withPropertyValues("yozmnews.embedding.openai-compatible.dimensions=4000",
					"yozmnews.embedding.openai-compatible.batch-size=256", "yozmnews.embedding.openai-compatible.api-key=")
			.run(context -> assertThat(context).hasSingleBean(ArticleEmbedder.class));
	}

	private void assertFails(String property, String name, String variable) {
		this.runner.withPropertyValues(property).run(context -> {
			assertThat(context).hasFailed();
			String message = rootMessage(context.getStartupFailure());
			assertThat(message).contains(name).contains(variable).doesNotContain(KEY);
		});
	}

	private static String rootMessage(Throwable failure) {
		Throwable cause = failure;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		return String.valueOf(cause.getMessage());
	}

}
