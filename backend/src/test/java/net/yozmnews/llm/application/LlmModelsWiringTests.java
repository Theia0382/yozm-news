package net.yozmnews.llm.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.LlmModels;
import net.yozmnews.llm.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 허용 모델 목록의 결선 — 설정 키가 운영자 파일을 가리키고, 공개 API 가 기동 때 읽은 목록을 낸다. 역할 profile 과 무관하다.
 * <p>
 * 지키는 규칙: llm-R3
 */
class LlmModelsWiringTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(ModelCatalogConfiguration.class, LlmModelsService.class);

	@Test
	void servesTheBundledListWithoutTheSetting() {
		this.runner.run(context -> {
			LlmModels models = context.getBean(LlmModels.class);

			assertThat(models.all()).hasSize(8);
			assertThat(models.of(Provider.OPENAI)).extracting(LlmModel::id)
				.containsExactly("gpt-6-astra", "gpt-6.1-sol", "gpt-6.1-terra", "gpt-6-luna");
			assertThat(models.find(Provider.ANTHROPIC, "claude-haiku-5-5"))
				.contains(new LlmModel(Provider.ANTHROPIC, "claude-haiku-5-5", 32_000, true));
			assertThat(models.find(Provider.ANTHROPIC, "claude-unknown")).isEmpty();
		});
	}

	@Test
	void theSettingPointsAtTheOperatorFile(@TempDir Path dir) throws IOException {
		Path file = Files.writeString(dir.resolve("llm-models.yaml"), """
				providers:
				  openai:
				    models:
				      - id: operator-model
				        input-token-budget: 7
				        structured-output: false
				""");

		this.runner.withPropertyValues("yozmnews.llm.models-file=" + file).run(context -> {
			assertThat(context.getBean(LlmModels.class).all())
				.containsExactly(new LlmModel(Provider.OPENAI, "operator-model", 7, false));
		});
	}

	@Test
	void aMissingOperatorFileKeepsTheContextFromStarting(@TempDir Path dir) {
		this.runner.withPropertyValues("yozmnews.llm.models-file=" + dir.resolve("nowhere.yaml")).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("YOZMNEWS_LLM_MODELS_FILE");
		});
	}

	@Test
	void anEmptySettingKeepsTheContextFromStarting() {
		this.runner.withPropertyValues("yozmnews.llm.models-file=").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("가 비었다");
		});
	}

}
