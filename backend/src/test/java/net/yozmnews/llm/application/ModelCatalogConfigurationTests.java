package net.yozmnews.llm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.domain.catalog.ModelCatalog;
import net.yozmnews.llm.domain.catalog.ModelCatalogParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/**
 * 허용 모델 목록의 기동 적재 — 운영자 파일이 목록 전체를 바꾸고, 가리킨 파일이 없거나 틀리면 기본 목록으로 떨어지지 않고 멈춘다.
 * <p>
 * 지키는 규칙: llm-R3 · llm-R4
 */
class ModelCatalogConfigurationTests {

	private static final Resource DEFAULTS = new DefaultResourceLoader()
		.getResource(ModelCatalogConfiguration.DEFAULT_LOCATION);

	private final ModelCatalogParser parser = new ModelCatalogParser();

	@Test
	void theBundledFileIsTheListWhenNoOperatorFileIsSet() {
		ModelCatalog catalog = ModelCatalogConfiguration.load(null, DEFAULTS, this.parser);

		assertThat(ModelCatalogConfiguration.DEFAULT_LOCATION).isEqualTo("classpath:llm-models/default.yaml");
		assertThat(catalog.all()).containsExactly(new LlmModel(Provider.OPENAI, "gpt-6-astra", 32_000, true),
				new LlmModel(Provider.OPENAI, "gpt-6.1-sol", 32_000, true),
				new LlmModel(Provider.OPENAI, "gpt-6.1-terra", 32_000, true),
				new LlmModel(Provider.OPENAI, "gpt-6-luna", 32_000, true),
				new LlmModel(Provider.ANTHROPIC, "claude-fable-5-2", 32_000, true),
				new LlmModel(Provider.ANTHROPIC, "claude-opus-5-5", 32_000, true),
				new LlmModel(Provider.ANTHROPIC, "claude-sonnet-5-5", 32_000, true),
				new LlmModel(Provider.ANTHROPIC, "claude-haiku-5-5", 32_000, true));
	}

	@Test
	void anOperatorFileReplacesTheWholeList(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("llm-models.yaml"), """
				providers:
				  anthropic:
				    models:
				      - id: only-this
				        input-token-budget: 500
				        structured-output: false
				""");

		ModelCatalog catalog = ModelCatalogConfiguration.load(file.toString(), DEFAULTS, this.parser);

		assertThat(catalog.all()).containsExactly(new LlmModel(Provider.ANTHROPIC, "only-this", 500, false));
		assertThat(catalog.find(Provider.OPENAI, "gpt-6-astra")).isEmpty();
	}

	@Test
	void aMissingOperatorFileStopsStartupNamingTheSetting(@TempDir Path dir) {
		String missing = dir.resolve("nowhere.yaml").toString();

		assertThatIllegalStateException().isThrownBy(() -> ModelCatalogConfiguration.load(missing, DEFAULTS, this.parser))
			.withMessage("yozmnews.llm.models-file(환경 변수 YOZMNEWS_LLM_MODELS_FILE) 가 가리키는 허용 모델 목록 파일(" + missing
					+ ")이 없거나 읽을 수 없다");
	}

	@Test
	void aDirectoryInPlaceOfTheOperatorFileStopsStartup(@TempDir Path dir) {
		assertThatIllegalStateException()
			.isThrownBy(() -> ModelCatalogConfiguration.load(dir.toString(), DEFAULTS, this.parser))
			.withMessageContaining("YOZMNEWS_LLM_MODELS_FILE");
	}

	@Test
	void anInvalidOperatorFileStopsStartupWithEveryProblem(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("llm-models.yaml"), """
				providers:
				  openai:
				    models:
				      - id: m
				        input-token-budget: 0
				        structured-output: maybe
				""");

		assertThatIllegalStateException()
			.isThrownBy(() -> ModelCatalogConfiguration.load(file.toString(), DEFAULTS, this.parser))
			.withMessage("yozmnews.llm.models-file(환경 변수 YOZMNEWS_LLM_MODELS_FILE) 가 가리키는 허용 모델 목록 파일(" + file
					+ ")의 형식이 틀렸다\n"
					+ "  5:29 providers.openai.models[0].input-token-budget: 입력 토큰 예산은 1 이상 10000000 이하여야 한다 (지금 0)\n"
					+ "  6:28 providers.openai.models[0].structured-output: true 또는 false 여야 한다");
	}

	@Test
	void aBlankSettingStopsStartupInsteadOfFallingBackToTheBundledFile() {
		assertThatIllegalStateException().isThrownBy(() -> ModelCatalogConfiguration.load("", DEFAULTS, this.parser))
			.withMessage("yozmnews.llm.models-file(환경 변수 YOZMNEWS_LLM_MODELS_FILE) 가 비었다 — 운영자 파일을 쓰지 않으면 줄을 지운다");
		assertThatIllegalStateException().isThrownBy(() -> ModelCatalogConfiguration.load("  ", DEFAULTS, this.parser))
			.withMessageContaining("가 비었다");
	}

	@Test
	void aBrokenBundledFileStopsStartup() {
		Resource broken = new ByteArrayResource("providers: {}\n".getBytes(), "시험용 기본 파일");

		assertThatIllegalStateException().isThrownBy(() -> ModelCatalogConfiguration.load(null, broken, this.parser))
			// 괄호 안은 Spring 이 자원을 가리키는 말이다
			.withMessage("기본 허용 모델 목록 파일(Byte array resource [시험용 기본 파일])의 형식이 틀렸다\n"
					+ "  1:1 providers: 모델이 하나도 없다 — 전체 모델은 하나 이상이어야 한다");
		assertThatIllegalStateException()
			.isThrownBy(() -> ModelCatalogConfiguration.load(null, new ClassPathResource("llm-models/none.yaml"),
					this.parser))
			.withMessageContaining("이 없거나 읽을 수 없다");
	}

}
