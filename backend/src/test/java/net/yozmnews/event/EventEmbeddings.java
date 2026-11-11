package net.yozmnews.event;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.evaluation.EmbedderInfo;
import net.yozmnews.evaluation.Embedding;
import net.yozmnews.evaluation.EmbeddingResult;
import net.yozmnews.evaluation.Embeddings;
import net.yozmnews.evaluation.TextEmbedding;

/**
 * 시험 대역 — 사건 묶기 시험이 회차에 넣는 {@code evaluation} 공개 API {@link TextEmbedding}. 외부 경계의 대역이라 관문의 결과 확인 ·
 * 트랜잭션 검사는 하지 않는다. 대체물의 벡터 규칙을 다시 구현하지 않는다 — 벡터는 이 대역의 고정물이다.
 */
public final class EventEmbeddings {

	/** {@link #hashing()} 의 이름 */
	public static final String HASHING_NAME = "시험";

	/** {@link #hashing()} 의 모델 버전 */
	public static final String HASHING_MODEL = "test-hash-8-1";

	/** {@link #hashing()} 의 차원 */
	public static final int HASHING_DIMENSIONS = 8;

	private EventEmbeddings() {
	}

	/** 모델 버전만 밝히는 대역 — 배정 회차는 임베딩하지 않는다 */
	public static TextEmbedding modelOnly(String modelVersion) {
		EmbedderInfo info = new EmbedderInfo("시험", modelVersion, 2);
		return new TextEmbedding() {

			@Override
			public EmbedderInfo info() {
				return info;
			}

			@Override
			public EmbeddingResult embed(List<String> texts) {
				throw new AssertionError("배정 회차는 임베딩하지 않는다");
			}

		};
	}

	/**
	 * 텍스트마다 {@code String.hashCode()} 를 {@value #HASHING_DIMENSIONS} 으로 나눈 나머지({@code Math.floorMod}) 칸이 1 인
	 * 단위 벡터를 내는 대역 — 같은 텍스트면 같은 벡터다.
	 */
	public static TextEmbedding hashing() {
		return new Hashing();
	}

	/** {@link #hashing()} 이 텍스트 하나에 내는 벡터 */
	public static float[] hashed(String text) {
		float[] values = new float[HASHING_DIMENSIONS];
		values[Math.floorMod(text.hashCode(), HASHING_DIMENSIONS)] = 1f;
		return values;
	}

	private static final class Hashing implements TextEmbedding {

		private static final EmbedderInfo INFO = new EmbedderInfo(HASHING_NAME, HASHING_MODEL, HASHING_DIMENSIONS);

		@Override
		public EmbedderInfo info() {
			return INFO;
		}

		@Override
		public EmbeddingResult embed(List<String> texts) {
			List<Embedding> vectors = new ArrayList<>(texts.size());
			for (String text : texts) {
				vectors.add(Embedding.of(hashed(text)));
			}
			return new Embeddings(vectors);
		}

	}

}
