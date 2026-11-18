package net.yozmnews.llm.domain.schema;

import java.util.Set;

/**
 * 출력 스키마를 Anthropic 구조화 출력이 받는 꼴로 맞추는 정본 — docs/specs/llm.md llm-R21.
 * <p>
 * 문자열 · 수 제약과 복잡한 배열 제약을 받지 않고, 객체마다 {@code additionalProperties: false} 를 요구한다. 선택 속성은 받으므로
 * {@code required} 는 그대로 둔다. 지운 규칙은 부르는 쪽의 읽기 · 검증이 맡는다.
 */
public final class AnthropicSchemas {

	/** 남기는 키워드 */
	static final Set<String> ALLOWED = Set.of("type", "properties", "required", "additionalProperties", "items",
			"enum", "description");

	private static final SchemaRewrite REWRITE = new SchemaRewrite(ALLOWED, SchemaRewrite.Required.AS_GIVEN);

	private AnthropicSchemas() {
	}

	/**
	 * @param schema 표준 JSON Schema 문서(JSON 객체)
	 * @throws IllegalArgumentException JSON 객체 문서가 아닐 때
	 */
	public static String supported(String schema) {
		return REWRITE.apply(schema);
	}

}
