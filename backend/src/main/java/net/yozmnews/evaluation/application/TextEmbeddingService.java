package net.yozmnews.evaluation.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import net.yozmnews.evaluation.EmbedderInfo;
import net.yozmnews.evaluation.EmbeddingFailure;
import net.yozmnews.evaluation.EmbeddingResult;
import net.yozmnews.evaluation.Embeddings;
import net.yozmnews.evaluation.TextEmbedding;
import net.yozmnews.evaluation.domain.embedder.ArticleEmbedder;
import net.yozmnews.evaluation.domain.embedder.EmbeddingCheck;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 공개 API {@link TextEmbedding} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R45.
 * <p>
 * 트랜잭션 밖인지 보고 임베더를 불러, 성공이면 결과의 수 · 차원을 {@link EmbeddingCheck} 로 확인한다. 어긋나면 예외이고, 예상한 실패는 그대로
 * 넘긴다. 결과를 기록하지 않는다.
 * <p>
 * 임베더는 부를 때 찾는다 — 화면 역할만 띄운 프로세스에는 임베더가 없고, 임베딩은 작업 역할의 작업에서 부른다. 트랜잭션 · 역할 애너테이션이
 * 없다(두 역할 공통).
 */
@Service
class TextEmbeddingService implements TextEmbedding {

	static final String NO_EMBEDDER = "임베더가 없는 프로세스에서 임베딩을 불렀다 — 임베딩은 작업 역할의 작업에서 부른다";

	static final String IN_TRANSACTION = "임베딩을 트랜잭션 안에서 불렀다 — 임베더 호출은 트랜잭션 밖에서 한다";

	/** 결과 어긋남 예외 문구의 머리 — 뒤에 {@link EmbeddingCheck} 의 어긋남 문구를 잇는다 */
	static final String MISMATCH = "임베더 결과가 어긋났다 — ";

	private final Supplier<@Nullable ArticleEmbedder> embedder;

	@Autowired
	TextEmbeddingService(ObjectProvider<ArticleEmbedder> embedders) {
		this(embedders::getIfAvailable);
	}

	/** 시험이 임베더를 넣는다 */
	TextEmbeddingService(Supplier<@Nullable ArticleEmbedder> embedder) {
		this.embedder = embedder;
	}

	@Override
	public EmbedderInfo info() {
		return embedder().info();
	}

	@Override
	public EmbeddingResult embed(List<String> texts) {
		Objects.requireNonNull(texts, "texts");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(IN_TRANSACTION);
		}
		ArticleEmbedder embedder = embedder();
		if (texts.isEmpty()) {
			return new Embeddings(List.of());
		}
		EmbedderInfo info = embedder.info();
		EmbeddingResult result = embedder.embed(texts);
		return switch (result) {
			case Embeddings embeddings -> {
				Optional<String> mismatch = EmbeddingCheck.mismatch(info, texts.size(), embeddings.vectors());
				if (mismatch.isPresent()) {
					throw new IllegalStateException(MISMATCH + mismatch.get());
				}
				yield embeddings;
			}
			case EmbeddingFailure failure -> failure;
		};
	}

	private ArticleEmbedder embedder() {
		ArticleEmbedder embedder = this.embedder.get();
		if (embedder == null) {
			throw new IllegalStateException(NO_EMBEDDER);
		}
		return embedder;
	}

}
