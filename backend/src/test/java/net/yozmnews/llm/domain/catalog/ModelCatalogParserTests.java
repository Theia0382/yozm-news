package net.yozmnews.llm.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import org.junit.jupiter.api.Test;

/**
 * 허용 모델 목록 파일의 형식 — 키 · 값의 범위 · 유일성 · 전체 수, 문제는 경로 · 줄:열과 함께 모두.
 * <p>
 * 지키는 규칙: llm-R2
 */
class ModelCatalogParserTests {

	private final ModelCatalogParser parser = new ModelCatalogParser();

	@Test
	void readsModelsInProviderOrderThenFileOrder() {
		ModelCatalog catalog = parsed("""
				providers:
				  anthropic:
				    models:
				      - id: claude-b
				        input-token-budget: 2000
				        structured-output: false
				      - id: claude-a
				        input-token-budget: 3000
				        structured-output: true
				  openai:
				    models:
				      - id: gpt-x
				        input-token-budget: 1000
				        structured-output: true
				""");

		assertThat(catalog.all()).containsExactly(new LlmModel(Provider.OPENAI, "gpt-x", 1000, true),
				new LlmModel(Provider.ANTHROPIC, "claude-b", 2000, false),
				new LlmModel(Provider.ANTHROPIC, "claude-a", 3000, true));
		assertThat(catalog.of(Provider.ANTHROPIC)).extracting(LlmModel::id).containsExactly("claude-b", "claude-a");
		assertThat(catalog.find(Provider.OPENAI, "gpt-x")).contains(new LlmModel(Provider.OPENAI, "gpt-x", 1000, true));
	}

	@Test
	void aModelOfAnotherProviderOrOutsideTheListIsNotFound() {
		ModelCatalog catalog = parsed(one("gpt-x", "1000", "true"));

		assertThat(catalog.find(Provider.ANTHROPIC, "gpt-x")).isEmpty();
		assertThat(catalog.find(Provider.OPENAI, "gpt-y")).isEmpty();
		assertThat(catalog.find(Provider.OPENAI, "GPT-X")).isEmpty();
	}

	@Test
	void aProviderMayBeAbsentOrHaveNoModels() {
		ModelCatalog catalog = parsed("""
				providers:
				  openai:
				    models: []
				  anthropic:
				    models:
				      - id: claude-a
				        input-token-budget: 1
				        structured-output: true
				""");

		assertThat(catalog.of(Provider.OPENAI)).isEmpty();
		assertThat(catalog.all()).containsExactly(new LlmModel(Provider.ANTHROPIC, "claude-a", 1, true));
		assertThat(parsed(one("gpt-x", "1000", "true")).of(Provider.ANTHROPIC)).isEmpty();
	}

	@Test
	void theBudgetRunsFromOneToTenMillion() {
		assertThat(parsed(one("m", "1", "true")).all()).extracting(LlmModel::inputTokenBudget).containsExactly(1);
		assertThat(parsed(one("m", "10000000", "true")).all()).extracting(LlmModel::inputTokenBudget)
			.containsExactly(10_000_000);
		assertThat(problems(one("m", "0", "true")))
			.containsExactly("5:29 providers.openai.models[0].input-token-budget: 입력 토큰 예산은 1 이상 10000000 이하여야 한다 (지금 0)");
		assertThat(problems(one("m", "10000001", "true"))).containsExactly(
				"5:29 providers.openai.models[0].input-token-budget: 입력 토큰 예산은 1 이상 10000000 이하여야 한다 (지금 10000001)");
		assertThat(problems(one("m", "1.5", "true")))
			.containsExactly("5:29 providers.openai.models[0].input-token-budget: 정수여야 한다");
	}

	@Test
	void theIdIsOneTo128PrintableAsciiCharacters() {
		assertThat(parsed(one("m".repeat(128), "1", "true")).all()).hasSize(1);
		assertThat(problems(one("m".repeat(129), "1", "true")))
			.containsExactly("4:13 providers.openai.models[0].id: 모델 ID 는 1자 이상 128자 이하여야 한다 (지금 129자)");
		assertThat(problems(one("\"\"", "1", "true")))
			.containsExactly("4:13 providers.openai.models[0].id: 모델 ID 는 1자 이상 128자 이하여야 한다 (지금 0자)");
		assertThat(problems(one("\"gpt x\"", "1", "true")))
			.containsExactly("4:13 providers.openai.models[0].id: 모델 ID 에는 공백 · 제어 문자 · ASCII 밖의 문자를 쓸 수 없다");
		assertThat(problems(one("모델", "1", "true")))
			.containsExactly("4:13 providers.openai.models[0].id: 모델 ID 에는 공백 · 제어 문자 · ASCII 밖의 문자를 쓸 수 없다");
		assertThat(problems(one("\"m\\x7F\"", "1", "true")))
			.containsExactly("4:13 providers.openai.models[0].id: 모델 ID 에는 공백 · 제어 문자 · ASCII 밖의 문자를 쓸 수 없다");
		assertThat(parsed(one("\"!~\"", "1", "true")).all()).extracting(LlmModel::id).containsExactly("!~");
	}

	@Test
	void theStructuredOutputFlagIsABoolean() {
		assertThat(parsed(one("m", "1", "false")).all()).extracting(LlmModel::structuredOutput).containsExactly(false);
		assertThat(problems(one("m", "1", "yes")))
			.containsExactly("6:28 providers.openai.models[0].structured-output: true 또는 false 여야 한다");
	}

	@Test
	void anIdIsUniqueWithinItsProviderOnly() {
		assertThat(problems("""
				providers:
				  openai:
				    models:
				      - id: same
				        input-token-budget: 1
				        structured-output: true
				      - id: same
				        input-token-budget: 2
				        structured-output: true
				""")).containsExactly("7:13 providers.openai.models[1].id: 같은 공급자에 같은 모델 ID 'same' 가 앞에 또 있다");
		assertThat(parsed("""
				providers:
				  openai:
				    models:
				      - id: same
				        input-token-budget: 1
				        structured-output: true
				  anthropic:
				    models:
				      - id: same
				        input-token-budget: 2
				        structured-output: true
				""").all()).hasSize(2);
	}

	@Test
	void everyModelFieldIsRequired() {
		assertThat(problems("""
				providers:
				  openai:
				    models:
				      - id: m
				""")).containsExactly("4:9 providers.openai.models[0].input-token-budget: 'input-token-budget' 가 없다",
				"4:9 providers.openai.models[0].structured-output: 'structured-output' 가 없다");
	}

	@Test
	void rejectsUnknownKeysAndUnknownProviders() {
		assertThat(problems("""
				version: 1
				providers:
				  gemini:
				    models: []
				  openai:
				    models:
				      - id: m
				        input-token-budget: 1
				        structured-output: true
				        context-window: 5
				""")).containsExactly("1:1 version: 알 수 없는 항목 'version'",
				"3:3 providers.gemini: 모르는 공급자 'gemini' — openai · anthropic 가운데 하나여야 한다",
				"10:9 providers.openai.models[0].context-window: 알 수 없는 항목 'context-window'");
		assertThat(problems("""
				providers:
				  openai:
				    models: []
				    region: eu
				""")).containsExactly("4:5 providers.openai.region: 알 수 없는 항목 'region'");
	}

	@Test
	void aProviderNeedsItsModelsList() {
		assertThat(problems("""
				providers:
				  openai: {}
				  anthropic:
				    models:
				      - id: m
				        input-token-budget: 1
				        structured-output: true
				""")).containsExactly("2:11 providers.openai.models: 'models' 가 없다 — 모델이 없는 공급자는 빈 목록([])으로 적는다");
	}

	@Test
	void needsAtLeastOneModelOverall() {
		assertThat(problems("""
				providers:
				  openai:
				    models: []
				  anthropic:
				    models: []
				""")).containsExactly("1:1 providers: 모델이 하나도 없다 — 전체 모델은 하나 이상이어야 한다");
		assertThat(problems("providers: {}\n")).containsExactly("1:1 providers: 모델이 하나도 없다 — 전체 모델은 하나 이상이어야 한다");
	}

	@Test
	void needsTheProvidersKey() {
		assertThat(problems("")).containsExactly("1:1 providers: 'providers' 가 없다");
	}

	@Test
	void reportsYamlProblemsOfThePlatformReader() {
		assertThat(problems("providers: [")).hasSize(1).first().asString().contains("YAML 문법 오류");
		assertThat(problems("#" + "x".repeat(65_536))).containsExactly("? (최상위): 원문은 65536자까지다 — 지금 65537자");
		assertThat(ModelCatalogParser.MAX_CHARS).isEqualTo(65_536);
	}

	private ModelCatalog parsed(String text) {
		ModelCatalogParse parse = this.parser.parse(text);
		assertThat(parse).isInstanceOf(ModelCatalogParse.Parsed.class);
		return ((ModelCatalogParse.Parsed) parse).catalog();
	}

	private List<String> problems(String text) {
		ModelCatalogParse parse = this.parser.parse(text);
		assertThat(parse).isInstanceOf(ModelCatalogParse.Invalid.class);
		return ((ModelCatalogParse.Invalid) parse).problems().stream().map(ModelCatalogProblem::toString).toList();
	}

	/** openai 에 모델 하나만 둔 원문 — id 는 4줄, 예산은 5줄, 구조화 출력은 6줄이다 */
	private static String one(String id, String budget, String structured) {
		return """
				providers:
				  openai:
				    models:
				      - id: %s
				        input-token-budget: %s
				        structured-output: %s
				""".formatted(id, budget, structured);
	}

}
