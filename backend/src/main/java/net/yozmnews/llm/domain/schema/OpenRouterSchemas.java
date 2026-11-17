package net.yozmnews.llm.domain.schema;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 출력 스키마를 OpenRouter 가 중계하는 공급자 모두가 받는 꼴로 맞추는 정본 — docs/specs/llm.md llm-R21.
 * <p>
 * OpenRouter 는 한 공급자 안에 여러 회사의 모델을 두고 실제 공급자로 옮겨 보낸다. 그래서 OpenAI 엄격 맞춤과 Anthropic 맞춤의 규칙을 함께
 * 만족하게 맞춘다 — 남기는 키워드는 두 맞춤의 허용 키워드 교집합이고, 모든 객체에 {@code additionalProperties: false} 를 두고, 모든 속성을
 * {@code required} 에 넣어 원래 선택이던 속성은 null 을 받게 한다. 값 목록({@code enum})이 있는 선택 속성은 타입을 지우고 값 목록에
 * null 을 더한다 — 값 목록과 {@code "null"} 을 더한 타입 배열을 함께 두면 Anthropic 계열이 거절한다(2026-11-17 실측). 지운 규칙은 부르는
 * 쪽의 읽기 · 검증이 맡는다.
 */
public final class OpenRouterSchemas {

	/** 남기는 키워드 — 두 공급자 맞춤의 허용 키워드 교집합 */
	static final Set<String> ALLOWED = OpenAiSchemas.ALLOWED.stream()
		.filter(AnthropicSchemas.ALLOWED::contains)
		.collect(Collectors.toUnmodifiableSet());

	private static final SchemaRewrite REWRITE = new SchemaRewrite(ALLOWED,
			SchemaRewrite.Required.ALL_NULL_IN_VALUES_WITHOUT_TYPE);

	private OpenRouterSchemas() {
	}

	/**
	 * @param schema 표준 JSON Schema 문서(JSON 객체)
	 * @throws IllegalArgumentException JSON 객체 문서가 아닐 때
	 */
	public static String common(String schema) {
		return REWRITE.apply(schema);
	}

}
