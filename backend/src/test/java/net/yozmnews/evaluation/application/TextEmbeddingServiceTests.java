package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.evaluation.EmbedderFailureKind;
import net.yozmnews.evaluation.EmbedderInfo;
import net.yozmnews.evaluation.Embedding;
import net.yozmnews.evaluation.EmbeddingFailure;
import net.yozmnews.evaluation.EmbeddingResult;
import net.yozmnews.evaluation.Embeddings;
import net.yozmnews.evaluation.domain.embedder.ArticleEmbedder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * 임베딩 공개 API — 임베더의 정보를 그대로 내고, 성공 결과의 수 · 차원을 확인해 어긋나면 예외이며, 예상한 실패는 그대로 넘긴다. 임베더가
 * 없는 프로세스에서 부르면 예외다.
 * <p>
 * 지키는 규칙: evaluation-R45
 */
class TextEmbeddingServiceTests {

	@Test
	void reportsTheEmbeddersInfo() {
		TextEmbeddingService service = new TextEmbeddingService(() -> new Scripted(3, null));

		assertThat(service.info()).isEqualTo(new EmbedderInfo("시험", "test-1", 3));
	}

	@Test
	void passesMatchingVectorsThroughInTheGivenOrder() {
		Scripted embedder = new Scripted(3, new Embeddings(List.of(Embedding.of(new float[] { 1f, 0f, 0f }),
				Embedding.of(new float[] { 0f, 0.5f, 0.25f }))));
		TextEmbeddingService service = new TextEmbeddingService(() -> embedder);

		EmbeddingResult result = service.embed(List.of("하나", "둘"));

		assertThat(result).isEqualTo(new Embeddings(List.of(Embedding.of(new float[] { 1f, 0f, 0f }),
				Embedding.of(new float[] { 0f, 0.5f, 0.25f }))));
		assertThat(embedder.calls).containsExactly(List.of("하나", "둘"));
	}

	@Test
	void passesAnExpectedFailureThrough() {
		TextEmbeddingService service = new TextEmbeddingService(
				() -> new Scripted(3, new EmbeddingFailure(EmbedderFailureKind.INPUT_REJECTED, "입력이 거부됐다")));

		assertThat(service.embed(List.of("하나")))
			.isEqualTo(new EmbeddingFailure(EmbedderFailureKind.INPUT_REJECTED, "입력이 거부됐다"));
	}

	@Test
	void anEmptyListIsAnEmptySuccessWithoutCallingTheEmbedder() {
		Scripted embedder = new Scripted(3, null);
		TextEmbeddingService service = new TextEmbeddingService(() -> embedder);

		assertThat(service.embed(List.of())).isEqualTo(new Embeddings(List.of()));
		assertThat(embedder.calls).isEmpty();
	}

	@Test
	void aVectorCountOtherThanTheTextCountIsAnException() {
		TextEmbeddingService service = new TextEmbeddingService(
				() -> new Scripted(3, new Embeddings(List.of(Embedding.of(new float[] { 1f, 0f, 0f })))));

		assertThatIllegalStateException().isThrownBy(() -> service.embed(List.of("하나", "둘")))
			.withMessage("임베더 결과가 어긋났다 — 벡터 수가 텍스트 수와 다르다 — 텍스트 2개, 벡터 1개");
	}

	@Test
	void aVectorOfAnotherDimensionIsAnException() {
		TextEmbeddingService service = new TextEmbeddingService(
				() -> new Scripted(4, new Embeddings(List.of(Embedding.of(new float[] { 1f, 0f, 0f })))));

		assertThatIllegalStateException().isThrownBy(() -> service.embed(List.of("하나")))
			.withMessage("임베더 결과가 어긋났다 — 벡터 차원이 임베더가 밝힌 차원과 다르다 — 0번째 3, 밝힌 차원 4");
	}

	@Test
	void aVectorOfTheDeclaredDimensionPasses() {
		// 대조 — 위 차원 어긋남과 같은 모양에서 밝힌 차원만 맞춘다
		TextEmbeddingService service = new TextEmbeddingService(
				() -> new Scripted(4, new Embeddings(List.of(Embedding.of(new float[] { 1f, 0f, 0f, 0f })))));

		assertThat(service.embed(List.of("하나")))
			.isEqualTo(new Embeddings(List.of(Embedding.of(new float[] { 1f, 0f, 0f, 0f }))));
	}

	@Test
	void failsWithoutAnEmbedder() {
		TextEmbeddingService service = new TextEmbeddingService(() -> null);

		assertThatIllegalStateException().isThrownBy(service::info)
			.withMessage("임베더가 없는 프로세스에서 임베딩을 불렀다 — 임베딩은 작업 역할의 작업에서 부른다");
		assertThatIllegalStateException().isThrownBy(() -> service.embed(List.of("하나")))
			.withMessage("임베더가 없는 프로세스에서 임베딩을 불렀다 — 임베딩은 작업 역할의 작업에서 부른다");
	}

	/** 정해 둔 결과를 내고 받은 묶음을 모으는 임베더 — 이름 "시험", 모델 버전 "test-1" */
	private static final class Scripted implements ArticleEmbedder {

		private final EmbedderInfo info;

		private final EmbeddingResult result;

		final List<List<String>> calls = new ArrayList<>();

		/** @param result 낼 결과 — null 이면 부르지 않을 자리라 사용 불가 실패를 낸다 */
		Scripted(int dimensions, @Nullable EmbeddingResult result) {
			this.info = new EmbedderInfo("시험", "test-1", dimensions);
			this.result = (result != null) ? result : new EmbeddingFailure(EmbedderFailureKind.EMBEDDER_UNAVAILABLE,
					"부르면 안 되는 시험 임베더");
		}

		@Override
		public EmbedderInfo info() {
			return this.info;
		}

		@Override
		public EmbeddingResult embed(List<String> texts) {
			this.calls.add(List.copyOf(texts));
			return this.result;
		}

	}

}
