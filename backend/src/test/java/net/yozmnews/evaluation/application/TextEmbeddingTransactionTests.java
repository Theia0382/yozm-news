package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.EmbedderInfo;
import net.yozmnews.evaluation.Embedding;
import net.yozmnews.evaluation.EmbeddingResult;
import net.yozmnews.evaluation.Embeddings;
import net.yozmnews.evaluation.TextEmbedding;
import net.yozmnews.evaluation.domain.embedder.ArticleEmbedder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 임베딩 공개 API 와 트랜잭션 — 결선된 운영 구현({@link TextEmbedding} 빈)을 트랜잭션 안에서 부르면 임베더를 부르기 전에 예외다. 임베더는
 * 부른 묶음을 모으는 시험 빈이다.
 * <p>
 * 지키는 규칙: evaluation-R45
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class TextEmbeddingTransactionTests {

	@Autowired
	TextEmbedding embedding;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	Counting embedder;

	@BeforeEach
	void reset() {
		this.embedder.calls.clear();
	}

	@Test
	void embeddingInsideATransactionFailsBeforeCallingTheEmbedder() {
		assertThatIllegalStateException()
			.isThrownBy(() -> this.transaction.executeWithoutResult(status -> this.embedding.embed(List.of("하나"))))
			.withMessage("임베딩을 트랜잭션 안에서 불렀다 — 임베더 호출은 트랜잭션 밖에서 한다");
		assertThat(this.embedder.calls).isEmpty();
	}

	@Test
	void embeddingOutsideATransactionCallsTheEmbedderOnce() {
		// 대조 — 같은 호출을 트랜잭션 밖에서 하면 시험 임베더가 한 번 불려 그 결과가 나온다
		EmbeddingResult result = this.embedding.embed(List.of("하나"));

		assertThat(result).isEqualTo(new Embeddings(List.of(Embedding.of(new float[] { 1f, 0f }))));
		assertThat(this.embedder.calls).containsExactly(List.of("하나"));
	}

	/** 텍스트마다 벡터 (1, 0) 을 내고 부른 묶음을 모으는 임베더 */
	static final class Counting implements ArticleEmbedder {

		final List<List<String>> calls = new ArrayList<>();

		@Override
		public EmbedderInfo info() {
			return new EmbedderInfo("시험", "test-1", 2);
		}

		@Override
		public EmbeddingResult embed(List<String> texts) {
			this.calls.add(List.copyOf(texts));
			return new Embeddings(texts.stream().map(text -> Embedding.of(new float[] { 1f, 0f })).toList());
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class CountingEmbedder {

		@Bean
		@Primary
		Counting countingEmbedder() {
			return new Counting();
		}

	}

}
