package net.yozmnews.evaluation.adapter.out.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.Embedding;
import net.yozmnews.evaluation.EmbeddingResult;
import net.yozmnews.evaluation.Embeddings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 기본 로컬 임베딩 — 실제 모델 파일(빌드가 받은 multilingual-e5-small int8)로 정보 · 길이 · 결정성 · 함께 넘긴 텍스트와 무관함 · 기준 벡터
 * 대조 · 사건 구별 · 텔레메트리 검사를 본다.
 * <p>
 * 기준 벡터(reference-vectors.json — 키 a · b · c 가 문장 A · B · C)는 이 구현과 독립인 파이썬 sentence-transformers 로 시험 밖에서 구한
 * 값이다 — {@code SentenceTransformer("intfloat/multilingual-e5-small", revision=embedding-model.properties 의 revision)} 의 원본 fp32
 * 가중치로 {@code encode("query: " + 문장, normalize_embeddings=True)}. int8 · CPU 차이로 값이 조금 다르므로 허용 오차를 둔다.
 * <p>
 * 지키는 규칙: ADR-0091 · ADR-0092(로컬 구현은 입력 거부를 내지 않는다)
 */
class OnnxArticleEmbedderTests {

	private static final String A = "한국은행 기준금리 동결\n한국은행이 기준금리를 연 3.25%로 동결했다.";

	private static final String B = "한은, 기준금리 연 3.25% 유지\n한국은행 금융통화위원회가 기준금리를 동결했다.";

	private static final String C = "프로야구 한국시리즈 7차전 개막\n삼성과 KT가 우승을 놓고 맞붙는다.";

	/** 기준 벡터 a 의 앞 8 값 — 파이썬 출력에서 옮겨 적었다 */
	private static final double[] A_HEAD = { 0.022902, -0.017666, -0.010672, -0.040976, 0.086783, 0.006894, 0.027483,
			0.078099 };

	/** 기준 벡터 c 의 앞 8 값 */
	private static final double[] C_HEAD = { 0.052456, -0.001667, -0.021247, -0.038747, 0.117604, -0.006674, 0.000944,
			0.010855 };

	private static OnnxArticleEmbedder embedder;

	@BeforeAll
	static void open() {
		embedder = new OnnxArticleEmbedder(EmbeddingModel.load());
	}

	@AfterAll
	static void close() throws Exception {
		embedder.close();
	}

	@Test
	void announcesTheModelVersionAndDimensions() {
		assertThat(embedder.info().name()).isEqualTo("onnx");
		assertThat(embedder.info().modelVersion()).isEqualTo("intfloat/multilingual-e5-small@614241f6:qint8");
		assertThat(embedder.info().dimensions()).isEqualTo(384);
	}

	@Test
	void vectorsAreUnitLengthAndDeterministic() {
		List<Embedding> first = vectors(List.of(A, C));
		List<Embedding> again = vectors(List.of(A, C));

		assertThat(first).hasSize(2).isEqualTo(again);
		for (Embedding vector : first) {
			assertThat(vector.dimensions()).isEqualTo(384);
			assertThat(norm(vector.values())).isCloseTo(1.0, within(1e-5));
		}
	}

	@Test
	void matchesTheIndependentReferenceVectors() throws IOException {
		JsonNode reference = reference();
		List<Embedding> vectors = vectors(List.of(A, C));

		assertThat(cosine(vectors.get(0).values(), array(reference.get("a")))).isGreaterThanOrEqualTo(0.99);
		assertThat(cosine(vectors.get(1).values(), array(reference.get("c")))).isGreaterThanOrEqualTo(0.99);
		for (int i = 0; i < 8; i++) {
			assertThat((double) vectors.get(0).values()[i]).isCloseTo(A_HEAD[i], within(0.02));
			assertThat((double) vectors.get(1).values()[i]).isCloseTo(C_HEAD[i], within(0.02));
		}
	}

	@Test
	void theSameEventIsCloserThanAnotherEvent() {
		List<Embedding> vectors = vectors(List.of(A, B, C));

		assertThat(cosine(vectors.get(0).values(), vectors.get(1).values()))
			.isGreaterThan(cosine(vectors.get(0).values(), vectors.get(2).values()));
	}

	@Test
	void aTextGetsTheSameVectorWhateverItIsEmbeddedWith() {
		List<String> texts = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			texts.add((i % 2 == 0) ? A + " " + i : C + " 추가 문장 " + i + " " + "길이를 바꾸는 말 ".repeat(i));
		}

		List<Embedding> together = vectors(texts);

		assertThat(together).hasSize(10);
		for (int i = 0; i < 10; i++) {
			assertThat(together.get(i)).isEqualTo(vectors(List.of(texts.get(i))).getFirst());
		}
		assertThat(vectors(List.of())).isEmpty();
	}

	@Test
	void refusesToOpenTheModelWithoutTheTelemetryVariable() {
		assertThatIllegalStateException().isThrownBy(() -> new OnnxArticleEmbedder(EmbeddingModel.load(), Map.of()))
			.withMessageContaining("ORT_DISABLE_TELEMETRY=1");
		assertThatIllegalStateException()
			.isThrownBy(() -> OnnxArticleEmbedder.requireTelemetryDisabled(Map.of("ORT_DISABLE_TELEMETRY", "0")));
		OnnxArticleEmbedder.requireTelemetryDisabled(Map.of("ORT_DISABLE_TELEMETRY", "1"));
	}

	private static List<Embedding> vectors(List<String> texts) {
		EmbeddingResult result = embedder.embed(texts);
		assertThat(result).isInstanceOf(Embeddings.class);
		return ((Embeddings) result).vectors();
	}

	private static JsonNode reference() throws IOException {
		try (InputStream input = OnnxArticleEmbedderTests.class.getResourceAsStream("reference-vectors.json")) {
			assertThat(input).isNotNull();
			return JsonMapper.builder().build().readTree(input);
		}
	}

	private static float[] array(JsonNode values) {
		assertThat(values.size()).isEqualTo(384);
		float[] array = new float[values.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = (float) values.get(i).doubleValue();
		}
		return array;
	}

	private static double norm(float[] values) {
		double sum = 0;
		for (float value : values) {
			sum += (double) value * value;
		}
		return Math.sqrt(sum);
	}

	private static double cosine(float[] a, float[] b) {
		double dot = 0;
		for (int i = 0; i < a.length; i++) {
			dot += (double) a[i] * b[i];
		}
		return dot / (norm(a) * norm(b));
	}

}
