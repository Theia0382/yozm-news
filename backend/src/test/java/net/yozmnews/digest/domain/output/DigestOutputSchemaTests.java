package net.yozmnews.digest.domain.output;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 출력 구조의 JSON Schema 문서 — 서술을 그린 문서가 손으로 쓴 문서와 같다. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R4 · digest-R5
 * <p>
 * 오라클은 손으로 쓴 JSON Schema 문서 한 벌이다. 서술에서 다시 그려 기대값으로 쓰지 않는다 — 서술이 바뀌면 이 문서를 손으로 고친다.
 */
class DigestOutputSchemaTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String EXPECTED = """
			{
			  "$schema": "https://json-schema.org/draft/2020-12/schema",
			  "type": "object",
			  "properties": {
			    "overview": { "type": "string", "minLength": 1, "maxLength": 1000 },
			    "top_stories": {
			      "type": "array",
			      "items": {
			        "type": "object",
			        "properties": {
			          "title": { "type": "string", "minLength": 1, "maxLength": 200 },
			          "summary": { "type": "string", "minLength": 1, "maxLength": 2000 },
			          "refs": {
			            "type": "array",
			            "items": { "type": "string", "minLength": 1, "maxLength": 16 },
			            "maxItems": 20
			          }
			        },
			        "required": ["title", "summary", "refs"],
			        "additionalProperties": false
			      },
			      "maxItems": 20
			    },
			    "more_headlines": {
			      "type": "array",
			      "items": {
			        "type": "object",
			        "properties": {
			          "ref": { "type": "string", "minLength": 1, "maxLength": 16 },
			          "note": { "type": ["string", "null"], "maxLength": 300 }
			        },
			        "required": ["ref"],
			        "additionalProperties": false
			      },
			      "maxItems": 60
			    }
			  },
			  "required": ["overview", "top_stories", "more_headlines"],
			  "additionalProperties": false
			}
			""";

	@Test
	void drawsTheShapeAsTheHandWrittenJsonSchema() {
		JsonNode drawn = JSON.readTree(DigestOutputSchema.json());
		JsonNode expected = JSON.readTree(EXPECTED);

		assertThat(drawn).isEqualTo(expected);
	}

}
