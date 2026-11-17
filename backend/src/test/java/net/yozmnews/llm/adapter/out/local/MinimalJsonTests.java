package net.yozmnews.llm.adapter.out.local;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 로컬 대체물의 생성 본문 — 스키마의 필수 속성만 채운 가장 작은 JSON 을 손으로 적은 기대 문서와 비교한다.
 * <p>
 * 지키는 규칙: llm-R16
 */
class MinimalJsonTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void fillsOnlyTheRequiredPropertiesOfTheDigestLikeSchema() {
		String schema = """
				{"type":"object","properties":{
				  "overview":{"type":"string","minLength":1,"maxLength":1000},
				  "top_stories":{"type":"array","items":{"type":"object","properties":{
				    "title":{"type":"string"},"refs":{"type":"array","items":{"type":"string"}}},
				    "required":["title","refs"]}},
				  "more_headlines":{"type":"array","minItems":1,"items":{"type":"object","properties":{
				    "ref":{"type":"string","maxLength":2},"note":{"type":["null","string"]}},
				    "required":["ref"]}},
				  "skipped":{"type":"string"}},
				 "required":["overview","top_stories","more_headlines"]}
				""";

		assertSameJson(MinimalJson.of(schema), """
				{"overview":"로컬 대체물이 만든 본문이다.","top_stories":[],"more_headlines":[{"ref":"로컬"}]}
				""");
	}

	@Test
	void takesTheFirstValueOfAValueListAndTheZeroOfEachType() {
		String schema = """
				{"type":"object","properties":{
				  "kind":{"type":"string","enum":["b","a"]},
				  "count":{"type":"integer"},"ratio":{"type":"number"},"flag":{"type":"boolean"},
				  "inner":{"properties":{"x":{"type":"string","maxLength":0}},"required":["x"]},
				  "free":{}},
				 "required":["kind","count","ratio","flag","inner","free"]}
				""";

		assertSameJson(MinimalJson.of(schema), """
				{"kind":"b","count":0,"ratio":0,"flag":false,"inner":{"x":""},"free":null}
				""");
	}

	@Test
	void withoutASchemaTheBodyIsTheFixedText() {
		assertThat(MinimalJson.of(null)).isEqualTo("로컬 대체물이 만든 본문이다.");
	}

	private static void assertSameJson(String actual, String expected) {
		assertThat(JSON.readTree(actual)).as(actual).isEqualTo(JSON.readTree(expected));
	}

}
