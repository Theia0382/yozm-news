package net.yozmnews.digest.domain.output;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.digest.domain.item.ItemRules;
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
			          "ref": { "type": "string", "minLength": 1, "maxLength": 16 },
			          "title": { "type": "string", "minLength": 1, "maxLength": 200 },
			          "summary": { "type": "string", "minLength": 1, "maxLength": 2000 }
			        },
			        "required": ["ref", "title", "summary"],
			        "additionalProperties": false
			      },
			      "maxItems": 20
			    },
			    "headlines": {
			      "type": "array",
			      "items": {
			        "type": "object",
			        "properties": {
			          "ref": { "type": "string", "minLength": 1, "maxLength": 16 },
			          "title": { "type": "string", "minLength": 1, "maxLength": 200 },
			          "note": { "type": ["string", "null"], "maxLength": 300 }
			        },
			        "required": ["ref", "title"],
			        "additionalProperties": false
			      },
			      "maxItems": 150
			    }
			  },
			  "required": ["overview", "top_stories", "headlines"],
			  "additionalProperties": false
			}
			""";

	@Test
	void drawsTheShapeAsTheHandWrittenJsonSchema() {
		JsonNode drawn = JSON.readTree(DigestOutputSchema.json());
		JsonNode expected = JSON.readTree(EXPECTED);

		assertThat(drawn).isEqualTo(expected);
	}

	/**
	 * 봉인 — 주요 뉴스 상한 20 은 큰 슬롯 수 상한 10({@code user} 의 {@code DigestVolume.MAX_BIG_SLOTS})과 큰 슬롯 밖 주요 뉴스 수 5 의
	 * 합 이상이어야 한다. {@code digest} 의 두 상수는 손으로 적은 리터럴로 고정한다 — 어느 쪽이 바뀌면 실패해 다시 맞추게 한다
	 */
	@Test
	void theTopStoryLimitCoversTheBigSlotsAndTheOtherTopStories() {
		assertThat(ItemRules.TOP_STORIES).isEqualTo(5);
		assertThat(DigestOutputSchema.MAX_TOP_STORIES).isEqualTo(20);
		assertThat(DigestOutputSchema.MAX_TOP_STORIES)
			.isGreaterThanOrEqualTo(net.yozmnews.user.domain.interest.DigestVolume.MAX_BIG_SLOTS + ItemRules.TOP_STORIES);
		// 헤드라인 상한은 직접 입력한 최대 후보 수의 상한과 같다
		assertThat(DigestOutputSchema.MAX_HEADLINES).isEqualTo(150)
			.isEqualTo(net.yozmnews.user.domain.interest.DigestVolume.MAX_COUNT);
	}

}
