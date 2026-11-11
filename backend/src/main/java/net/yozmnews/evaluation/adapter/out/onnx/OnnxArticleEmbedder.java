package net.yozmnews.evaluation.adapter.out.onnx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import net.yozmnews.evaluation.EmbedderFailureKind;
import net.yozmnews.evaluation.EmbedderInfo;
import net.yozmnews.evaluation.Embedding;
import net.yozmnews.evaluation.EmbeddingFailure;
import net.yozmnews.evaluation.EmbeddingResult;
import net.yozmnews.evaluation.Embeddings;
import net.yozmnews.evaluation.domain.embedder.ArticleEmbedder;

/**
 * 기본 로컬 임베딩 구현 — multilingual-e5-small int8 을 DJL 토크나이저와 ONNX Runtime 으로 돌린다 · docs/specs/evaluation.md §4 · ADR-0091.
 * <p>
 * 입력마다 접두사를 붙이고, 텍스트 하나씩 추론해 마지막 은닉 상태를 평균 풀링한 뒤 {@link Embedding#normalized} 로 정규화한다. 여러 텍스트를
 * 한 번에 추론하면 int8 동적 양자화의 척도가 묶음 전체로 정해져 같은 텍스트의 벡터가 함께 묶인 텍스트에 따라 달라진다 — 하나씩이면 벡터가 텍스트로만
 * 정해진다(같은 CPU 에서). 추론 · 토크나이저 예외는 {@link EmbedderFailureKind#EMBEDDER_UNAVAILABLE}(문구는 예외 클래스 이름)이고 입력 거부는 내지 않는다 —
 * 토크나이저는 어떤 문자열이든 나누고 {@value #MAX_TOKENS} 토큰에서 자르므로 입력 탓으로 실패할 길이 사실상 없다(ADR-0092).
 * <p>
 * ONNX Runtime 을 올리기 전에 환경 변수 {@value #TELEMETRY_VARIABLE} 가 {@code 1} 인지 본다 — ONNX Runtime 을 올리는 곳이 이 생성자
 * 하나라 모든 실행 경로가 덮인다.
 */
final class OnnxArticleEmbedder implements ArticleEmbedder, AutoCloseable {

	/** 임베더 이름 */
	static final String NAME = "onnx";

	/** 연산 스레드 상한 — 가용 CPU 와 이 값 가운데 작은 값을 쓴다 */
	static final int MAX_THREADS = 4;

	/** 토크나이저가 자르는 길이 — 모델의 위치 임베딩 수 */
	static final int MAX_TOKENS = 512;

	/** ONNX Runtime 텔레메트리를 끄는 환경 변수 이름의 정본 — 배포 이미지 · Gradle 시험이 사본을 갖는다 */
	static final String TELEMETRY_VARIABLE = "ORT_DISABLE_TELEMETRY";

	private final EmbedderInfo info;

	private final String inputPrefix;

	private final HuggingFaceTokenizer tokenizer;

	private final OrtEnvironment environment;

	private final OrtSession session;

	private final boolean tokenTypes;

	/**
	 * @throws IllegalStateException 텔레메트리 변수가 없거나, 토크나이저 · 세션을 열지 못했을 때
	 */
	OnnxArticleEmbedder(EmbeddingModel model) {
		this(model, System.getenv());
	}

	/** 시험이 환경을 넘긴다 */
	OnnxArticleEmbedder(EmbeddingModel model, Map<String, String> environment) {
		Objects.requireNonNull(model, "model");
		requireTelemetryDisabled(environment);
		this.info = new EmbedderInfo(NAME, model.modelVersion(), model.dimensions());
		this.inputPrefix = model.inputPrefix();
		try {
			this.tokenizer = HuggingFaceTokenizer.newInstance(model.tokenizerFile(),
					Map.of("maxLength", Integer.toString(MAX_TOKENS), "truncation", "true", "padding", "false"));
		}
		catch (Exception ex) {
			throw new IllegalStateException("임베딩 토크나이저를 열지 못했다 — " + ex.getClass().getName(), ex);
		}
		try {
			this.environment = OrtEnvironment.getEnvironment();
			OrtSession.SessionOptions options = new OrtSession.SessionOptions();
			options.setIntraOpNumThreads(Math.min(Runtime.getRuntime().availableProcessors(), MAX_THREADS));
			options.setInterOpNumThreads(1);
			options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
			this.session = this.environment.createSession(model.modelFile().toString(), options);
			this.tokenTypes = this.session.getInputNames().contains("token_type_ids");
		}
		catch (OrtException ex) {
			this.tokenizer.close();
			throw new IllegalStateException("임베딩 모델을 열지 못했다 — " + ex.getClass().getName(), ex);
		}
	}

	/**
	 * ONNX Runtime 텔레메트리를 끄는 환경 변수가 {@code 1} 인지 본다.
	 * @throws IllegalStateException 아닐 때
	 */
	static void requireTelemetryDisabled(Map<String, String> environment) {
		if (!"1".equals(environment.get(TELEMETRY_VARIABLE))) {
			throw new IllegalStateException("ONNX Runtime 텔레메트리를 끄는 환경 변수 " + TELEMETRY_VARIABLE
					+ "=1 이 없어 임베딩 모델을 열지 않는다 — 배포 이미지는 이 값을 갖는다");
		}
	}

	@Override
	public EmbedderInfo info() {
		return this.info;
	}

	@Override
	public EmbeddingResult embed(List<String> texts) {
		Objects.requireNonNull(texts, "texts");
		List<Embedding> vectors = new ArrayList<>(texts.size());
		try {
			for (String text : texts) {
				vectors.add(infer(text));
			}
		}
		catch (OrtException | RuntimeException ex) {
			return new EmbeddingFailure(EmbedderFailureKind.EMBEDDER_UNAVAILABLE,
					"로컬 임베딩 추론이 실패했다 — " + ex.getClass().getName());
		}
		return new Embeddings(vectors);
	}

	private Embedding infer(String text) throws OrtException {
		long[] ids = this.tokenizer.encode(this.inputPrefix + text).getIds();
		long[] mask = new long[ids.length];
		Arrays.fill(mask, 1);
		Map<String, OnnxTensor> inputs = new HashMap<>();
		try {
			inputs.put("input_ids", OnnxTensor.createTensor(this.environment, new long[][] { ids }));
			inputs.put("attention_mask", OnnxTensor.createTensor(this.environment, new long[][] { mask }));
			if (this.tokenTypes) {
				inputs.put("token_type_ids", OnnxTensor.createTensor(this.environment, new long[1][ids.length]));
			}
			try (OrtSession.Result result = this.session.run(inputs)) {
				float[][][] hidden = (float[][][]) result.get(0).getValue();
				return meanPooled(hidden[0]);
			}
		}
		finally {
			inputs.values().forEach(OnnxTensor::close);
		}
	}

	/** 은닉 상태의 평균을 정규화한다 — 덧대기가 없어 모든 토큰이 마스크 1 이다 */
	private static Embedding meanPooled(float[][] hidden) {
		int tokens = hidden.length;
		int dimensions = hidden[0].length;
		double[] sum = new double[dimensions];
		for (int j = 0; j < tokens; j++) {
			for (int k = 0; k < dimensions; k++) {
				sum[k] += hidden[j][k];
			}
		}
		float[] mean = new float[dimensions];
		for (int k = 0; k < dimensions; k++) {
			mean[k] = (float) (sum[k] / tokens);
		}
		return Embedding.normalized(mean);
	}

	@Override
	public void close() throws OrtException {
		try {
			this.session.close();
		}
		finally {
			this.tokenizer.close();
		}
	}

}
