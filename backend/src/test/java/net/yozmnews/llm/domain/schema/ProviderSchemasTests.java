package net.yozmnews.llm.domain.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 출력 스키마의 공급자 맞춤 — 다이제스트 출력 스키마 꼴의 입력을 손으로 적은 기대 문서와 비교한다(키 순서는 보지 않는다).
 * <p>
 * 지키는 규칙: llm-R21
 */
class ProviderSchemasTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** 다이제스트 출력 스키마(`DigestOutputSchema.json()`)와 같은 꼴을 줄인 것 */
	private static final String DIGEST_LIKE = """
			{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
			 "properties":{
			  "overview":{"type":"string","minLength":1,"maxLength":1000},
			  "more_headlines":{"type":"array","maxItems":60,
			   "items":{"type":"object",
			    "properties":{
			     "ref":{"type":"string","minLength":1,"maxLength":16},
			     "note":{"type":["string","null"],"maxLength":300}},
			    "required":["ref"],"additionalProperties":false}}},
			 "required":["overview","more_headlines"],"additionalProperties":false}
			""";

	@Test
	void openAiDropsUnsupportedKeywordsAndRequiresEveryProperty() {
		assertSameJson(OpenAiSchemas.strict(DIGEST_LIKE), """
				{"type":"object",
				 "properties":{
				  "overview":{"type":"string"},
				  "more_headlines":{"type":"array","maxItems":60,
				   "items":{"type":"object",
				    "properties":{
				     "ref":{"type":"string"},
				     "note":{"type":["string","null"]}},
				    "required":["ref","note"],"additionalProperties":false}}},
				 "required":["overview","more_headlines"],"additionalProperties":false}
				""");
	}

	@Test
	void anthropicDropsUnsupportedKeywordsAndKeepsOptionalProperties() {
		assertSameJson(AnthropicSchemas.supported(DIGEST_LIKE), """
				{"type":"object",
				 "properties":{
				  "overview":{"type":"string"},
				  "more_headlines":{"type":"array",
				   "items":{"type":"object",
				    "properties":{
				     "ref":{"type":"string"},
				     "note":{"type":["string","null"]}},
				    "required":["ref"],"additionalProperties":false}}},
				 "required":["overview","more_headlines"],"additionalProperties":false}
				""");
	}

	@Test
	void openAiMakesAnOptionalPropertyNullableAndClosesAnOpenObject() {
		String schema = """
				{"type":"object","properties":{
				  "a":{"type":"string"},
				  "b":{"type":"integer","enum":[1,2]},
				  "c":{"type":["string","null"]},
				  "d":{"type":"object","properties":{"e":{"type":"boolean"}}}},
				 "required":["a"]}
				""";
		assertSameJson(OpenAiSchemas.strict(schema), """
				{"type":"object","properties":{
				  "a":{"type":"string"},
				  "b":{"type":["integer","null"],"enum":[1,2,null]},
				  "c":{"type":["string","null"]},
				  "d":{"type":["object","null"],"properties":{"e":{"type":["boolean","null"]}},
				       "required":["e"],"additionalProperties":false}},
				 "required":["a","b","c","d"],"additionalProperties":false}
				""");
	}

	@Test
	void openAiLetsAnOptionalValueListTakeNull() {
		// 값 목록만 있는 선택 속성도 null 을 받는다. 대조 — 필수 속성과 이미 null 을 가진 목록은 그대로다
		String schema = """
				{"type":"object","properties":{
				  "a":{"enum":["x","y"]},
				  "b":{"type":["string","null"],"enum":["x",null]},
				  "c":{"type":"string","enum":["x"]}},
				 "required":["c"]}
				""";
		assertSameJson(OpenAiSchemas.strict(schema), """
				{"type":"object","properties":{
				  "a":{"enum":["x","y",null]},
				  "b":{"type":["string","null"],"enum":["x",null]},
				  "c":{"type":"string","enum":["x"]}},
				 "required":["a","b","c"],"additionalProperties":false}
				""");
	}

	@Test
	void propertyNamesThatLookLikeKeywordsAreKept() {
		String schema = """
				{"type":"object","properties":{
				  "description":{"type":"string","format":"date"},
				  "type":{"type":"string"}},
				 "required":["description","type"]}
				""";
		String expected = """
				{"type":"object","properties":{
				  "description":{"type":"string"},
				  "type":{"type":"string"}},
				 "required":["description","type"],"additionalProperties":false}
				""";
		assertSameJson(OpenAiSchemas.strict(schema), expected);
		assertSameJson(AnthropicSchemas.supported(schema), expected);
	}

	@Test
	void aSchemaOfOnlySupportedKeywordsIsUnchanged() {
		// 대조 — 맞출 것이 없으면 그대로다
		String schema = """
				{"type":"object","properties":{"a":{"type":"string","description":"설명"}},
				 "required":["a"],"additionalProperties":false}
				""";
		assertSameJson(OpenAiSchemas.strict(schema), schema);
		assertSameJson(AnthropicSchemas.supported(schema), schema);
	}

	@Test
	void openRouterKeepsOnlyTheKeywordsBothProvidersTakeAndRequiresEveryProperty() {
		assertSameJson(OpenRouterSchemas.common(DIGEST_LIKE), """
				{"type":"object",
				 "properties":{
				  "overview":{"type":"string"},
				  "more_headlines":{"type":"array",
				   "items":{"type":"object",
				    "properties":{
				     "ref":{"type":"string"},
				     "note":{"type":["string","null"]}},
				    "required":["ref","note"],"additionalProperties":false}}},
				 "required":["overview","more_headlines"],"additionalProperties":false}
				""");
	}

	@Test
	void openRouterLetsAnOptionalValueListTakeNullWithoutAType() {
		// 대조 — OpenAI 엄격 맞춤은 같은 속성에 타입을 남긴다(OpenRouter 맞춤의 꼴이 틀려도 이 단언은 통과한다)
		assertSameJson(OpenAiSchemas.strict("""
				{"type":"object","properties":{"mood":{"type":"string","enum":["good","bad"]}}}
				"""), """
				{"type":"object","properties":{"mood":{"type":["string","null"],"enum":["good","bad",null]}},
				 "required":["mood"],"additionalProperties":false}
				""");
		assertSameJson(OpenRouterSchemas.common("""
				{"type":"object","properties":{"mood":{"type":"string","enum":["good","bad"]},
				 "count":{"type":"integer","minimum":0}},"required":["count"]}
				"""), """
				{"type":"object","properties":{"mood":{"enum":["good","bad",null]},
				 "count":{"type":"integer"}},"required":["mood","count"],"additionalProperties":false}
				""");
	}

	@Test
	void openRouterKeepsTheTypeOfARequiredValueListAndOfAnOptionalPropertyWithoutOne() {
		assertSameJson(OpenRouterSchemas.common("""
				{"type":"object","properties":{"mood":{"type":"string","enum":["good","bad"]},
				 "note":{"type":"string"}},"required":["mood"]}
				"""), """
				{"type":"object","properties":{"mood":{"type":"string","enum":["good","bad"]},
				 "note":{"type":["string","null"]}},"required":["mood","note"],"additionalProperties":false}
				""");
	}

	@Test
	void openRouterKeywordsAreTheKeywordsBothProvidersKeep() {
		assertThat(OpenRouterSchemas.ALLOWED).containsExactlyInAnyOrder("type", "properties", "required",
				"additionalProperties", "items", "enum", "description");
	}

	@Test
	void refusesADocumentThatIsNotAnObject() {
		assertThatThrownBy(() -> OpenAiSchemas.strict("[]")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AnthropicSchemas.supported("{\"type\":")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OpenAiSchemas.strict("{} []")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OpenRouterSchemas.common("[]")).isInstanceOf(IllegalArgumentException.class);
	}

	private static void assertSameJson(String actual, String expected) {
		JsonNode actualTree = JSON.readTree(actual);
		JsonNode expectedTree = JSON.readTree(expected);
		assertThat(actualTree).as(actual).isEqualTo(expectedTree);
	}

}
