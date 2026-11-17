package net.yozmnews.llm.domain.schema;

import java.util.Set;

/**
 * 출력 스키마를 OpenAI 구조화 출력(엄격 모드)이 받는 꼴로 맞추는 정본 — docs/specs/llm.md llm-R21.
 * <p>
 * 엄격 모드는 지원하지 않는 키워드가 있으면 요청을 거부하고, 모든 객체에 {@code additionalProperties: false} 와 모든 속성의
 * {@code required} 를 요구한다. 선택 속성은 null 을 받게 해(타입에 {@code "null"}, 값 목록({@code enum})에 null) 흉내 낸다. 지운
 * 규칙(문자열 길이 등)은 부르는 쪽의 읽기 · 검증이 맡는다.
 */
public final class OpenAiSchemas {

	/** 남기는 키워드 */
	static final Set<String> ALLOWED = Set.of("type", "properties", "required", "additionalProperties", "items",
			"enum", "description", "maxItems", "minItems");

	private static final SchemaRewrite REWRITE = new SchemaRewrite(ALLOWED, true);

	private OpenAiSchemas() {
	}

	/**
	 * @param schema 표준 JSON Schema 문서(JSON 객체)
	 * @throws IllegalArgumentException JSON 객체 문서가 아닐 때
	 */
	public static String strict(String schema) {
		return REWRITE.apply(schema);
	}

}
