package net.yozmnews.evaluation.domain.openai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI 호환 임베딩 요청 본문의 정본 — docs/specs/evaluation.md §4 {@code domain/openai}.
 * <p>
 * {@code model} · {@code input}, 차원을 넘기면 {@code dimensions} 를 담는다. {@code encoding_format} 은 넣지 않는다 — 기본이 float 이고,
 * float 만 받는 제공자가 있다(ADR-0090).
 */
public final class OpenAiEmbeddingRequest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private OpenAiEmbeddingRequest() {
	}

	/**
	 * @param inputs 입력 앞 문자열을 이미 붙인 텍스트
	 * @param dimensions 요청에 담을 차원 — 담지 않으면 null
	 */
	public static String body(String model, List<String> inputs, @Nullable Integer dimensions) {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(inputs, "inputs");
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("model", model);
		root.put("input", List.copyOf(inputs));
		if (dimensions != null) {
			root.put("dimensions", dimensions);
		}
		return JSON.writeValueAsString(root);
	}

}
