package net.yozmnews.digest.domain.output;

import static net.yozmnews.digest.DigestInputs.event;
import static net.yozmnews.digest.DigestInputs.item;
import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.digest.DigestInputs;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.item.ItemKind;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 출력 구조의 JSON Schema 문서 — 항목 집합에서 만든 서술을 그린 문서가 손으로 쓴 문서와 같다. 주요 뉴스 칸은 주요 뉴스 항목 별칭이 모두
 * 필수 키이고, 헤드라인 칸은 항목 수와 무관한 원소 배열이다.
 * 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R4 · digest-R5
 * <p>
 * 오라클은 손으로 쓴 JSON Schema 문서다. 서술에서 다시 그려 기대값으로 쓰지 않는다 — 서술이 바뀌면 이 문서를 손으로 고친다.
 */
class DigestOutputSchemaTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** 항목 I1 · I2(주요 뉴스) · I3(헤드라인) */
	private static final CandidateSet SET = CandidateSet.of(DigestInputs.items(
			item(ItemKind.TOP_STORY, event(1, SelectionReason.MAJOR_STORYLINE, 11)),
			item(ItemKind.TOP_STORY, event(2, SelectionReason.SUBSCRIBED, 12)),
			item(ItemKind.HEADLINE, event(3, SelectionReason.SUBSCRIBED, 13))));

	private static final String STORY = """
			{
			  "type": "object",
			  "properties": {
			    "title": { "type": "string", "minLength": 1, "maxLength": 200 },
			    "summary": { "type": "string", "minLength": 1, "maxLength": 2000 }
			  },
			  "required": ["title", "summary"],
			  "additionalProperties": false
			}""";

	private static final String HEADLINE = """
			{
			  "type": "object",
			  "properties": {
			    "ref": { "type": "string", "minLength": 1, "maxLength": 16 },
			    "title": { "type": "string", "minLength": 1, "maxLength": 200 },
			    "note": { "type": ["string", "null"], "maxLength": 300 }
			  },
			  "required": ["ref", "title"],
			  "additionalProperties": false
			}""";

	private static final String EXPECTED = """
			{
			  "$schema": "https://json-schema.org/draft/2020-12/schema",
			  "type": "object",
			  "properties": {
			    "overview": { "type": "string", "minLength": 1, "maxLength": 1500 },
			    "top_stories": {
			      "type": "object",
			      "properties": { "I1": %1$s, "I2": %1$s },
			      "required": ["I1", "I2"],
			      "additionalProperties": false
			    },
			    "headlines": { "type": "array", "items": %2$s }
			  },
			  "required": ["overview", "top_stories", "headlines"],
			  "additionalProperties": false
			}
			""".formatted(STORY, HEADLINE);

	@Test
	void drawsEveryTopStoryAsARequiredKeyAndHeadlinesAsAnArrayOfElements() {
		JsonNode drawn = JSON.readTree(DigestOutputSchema.json(DigestOutputSchema.root(SET)));

		assertThat(drawn).isEqualTo(JSON.readTree(EXPECTED));
	}

	@Test
	void drawsHeadlinesAsTheSameArrayWhenThereIsNoHeadlineItem() {
		CandidateSet storiesOnly = CandidateSet
			.of(DigestInputs.items(item(ItemKind.TOP_STORY, event(1, SelectionReason.SUBSCRIBED, 11))));

		JsonNode headlines = JSON.readTree(DigestOutputSchema.json(DigestOutputSchema.root(storiesOnly)))
			.get("properties")
			.get("headlines");

		assertThat(headlines).isEqualTo(JSON.readTree("{ \"type\": \"array\", \"items\": %s }".formatted(HEADLINE)));
	}

	@Test
	void drawsTopStoriesWithoutItemsAsAnEmptyObject() {
		CandidateSet headlinesOnly = CandidateSet
			.of(DigestInputs.items(item(ItemKind.HEADLINE, event(1, SelectionReason.SUBSCRIBED, 11))));

		JsonNode topStories = JSON.readTree(DigestOutputSchema.json(DigestOutputSchema.root(headlinesOnly)))
			.get("properties")
			.get("top_stories");

		assertThat(topStories).isEqualTo(JSON.readTree("""
				{ "type": "object", "properties": {}, "required": [], "additionalProperties": false }
				"""));
	}

}
