package net.yozmnews.event.domain.openai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import net.yozmnews.event.domain.feature.EmbedderFailureKind;
import net.yozmnews.event.domain.feature.Embedding;
import net.yozmnews.event.domain.feature.EmbeddingFailure;
import net.yozmnews.event.domain.feature.EmbeddingResult;
import net.yozmnews.event.domain.feature.Embeddings;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI 호환 임베딩 응답(2xx 본문) 읽기의 정본 — docs/specs/event.md §4 {@code domain/openai}.
 * <p>
 * {@code data[]} 를 {@code index} 로 정렬해 수를 확인하고, 벡터마다 {@link Embedding#normalized} 로 정규화한다 — 정규화하지 않은 벡터를 주는
 * 제공자가 있다. 읽을 수 없으면 {@link EmbedderFailureKind#EMBEDDER_UNAVAILABLE} 이고, 문구에 본문 조각을 싣지 않는다.
 */
public final class OpenAiEmbeddingResponse {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String UNREADABLE = "외부 임베딩 응답을 읽을 수 없다 — ";

	private OpenAiEmbeddingResponse() {
	}

	/**
	 * @param expected 넘긴 텍스트 수
	 */
	public static EmbeddingResult read(String body, int expected) {
		Objects.requireNonNull(body, "body");
		JsonNode root;
		try {
			root = JSON.readTree(body);
		}
		catch (JacksonException ex) {
			return unreadable("JSON 이 아니다");
		}
		if (root == null || !root.isObject()) {
			return unreadable("JSON 객체가 아니다");
		}
		JsonNode data = root.get("data");
		if (data == null || !data.isArray()) {
			return unreadable("data 가 배열이 아니다");
		}
		if (data.size() != expected) {
			return unreadable("벡터 수가 다르다 — 넘긴 텍스트 " + expected + "개, 받은 벡터 " + data.size() + "개");
		}
		Embedding[] ordered = new Embedding[expected];
		for (JsonNode item : data) {
			JsonNode index = (item != null && item.isObject()) ? item.get("index") : null;
			if (index == null || !index.isIntegralNumber() || !index.canConvertToInt()) {
				return unreadable("index 가 정수가 아니다");
			}
			int at = index.intValue();
			if (at < 0 || at >= expected) {
				return unreadable("index 가 범위 밖이다 — " + at);
			}
			if (ordered[at] != null) {
				return unreadable("index 가 겹친다 — " + at);
			}
			JsonNode embedding = item.get("embedding");
			if (embedding == null || !embedding.isArray() || embedding.isEmpty()) {
				return unreadable(at + "번째 embedding 이 수 배열이 아니다");
			}
			float[] values = new float[embedding.size()];
			for (int i = 0; i < values.length; i++) {
				JsonNode value = embedding.get(i);
				if (value == null || !value.isNumber()) {
					return unreadable(at + "번째 embedding 이 수 배열이 아니다");
				}
				values[i] = (float) value.doubleValue();
			}
			try {
				ordered[at] = Embedding.normalized(values);
			}
			catch (IllegalArgumentException ex) {
				return unreadable(at + "번째 벡터를 정규화할 수 없다 — 유한하지 않은 값이나 영벡터");
			}
		}
		List<Embedding> vectors = new ArrayList<>(Arrays.asList(ordered));
		return new Embeddings(vectors);
	}

	private static EmbeddingFailure unreadable(String reason) {
		return new EmbeddingFailure(EmbedderFailureKind.EMBEDDER_UNAVAILABLE, UNREADABLE + reason);
	}

}
