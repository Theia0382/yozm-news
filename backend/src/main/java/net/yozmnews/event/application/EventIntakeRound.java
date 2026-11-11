package net.yozmnews.event.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EmbedderInfo;
import net.yozmnews.evaluation.Embedding;
import net.yozmnews.evaluation.EmbeddingFailure;
import net.yozmnews.evaluation.EmbeddingResult;
import net.yozmnews.evaluation.Embeddings;
import net.yozmnews.evaluation.TextEmbedding;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.feature.KeyTermExtractor;
import net.yozmnews.event.domain.feature.MatchFeatures;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import net.yozmnews.event.domain.feature.MatchText;
import net.yozmnews.event.domain.intake.IntakeQueueRepository;
import net.yozmnews.event.domain.intake.IntakeSchedule;
import net.yozmnews.event.domain.intake.PendingArticle;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사건 묶기 회차 하나 — docs/specs/event.md §4 · event-R6 · event-R8.
 * <p>
 * 대기를 점유해 커밋한 뒤, 트랜잭션 밖에서 기사를 읽고 표현 텍스트 · 핵심 말을 만들어 임베더를 한 번 부른다. 결과는 기사마다 짧은 트랜잭션에서
 * 점유 토큰이 맞아 대기 행을 지웠을 때만 쓰고, 같은 트랜잭션에서 그 기사를 배정 대기에 넣는다(표현 행이 이미 있어도 넣는다). 일시 실패 ·
 * 임베더 사용 불가 · 예외 · 결과 어긋남이면 남은 점유를 풀고 회차를 끝낸다. 입력 거부면 기사 하나씩 다시 불러 거부된 기사의 거부 수를 올리고,
 * 상한이면 대기에서 뺀다. 임베더의 예외를 밖으로 던지지 않는다 — 점유 · 기사 조회 · 표현 텍스트 · 핵심 말 · 쓰기 · 점유 풀기의 예외는
 * 회차 밖으로 나가고, 그때 점유한 기사는 점유가 만료된 뒤 다시 처리된다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
public class EventIntakeRound {

	private static final Logger logger = LoggerFactory.getLogger(EventIntakeRound.class);

	/** 일시 실패로 멈출 때의 경고 — 이름 · 모델 버전 · 남은 점유 수 · 문구 */
	private static final String TRANSIENT_STOP = "임베더가 일시 실패해 회차를 멈췄다 — {} {}, 기사 {}건: {}";

	/** 임베더 사용 불가로 멈출 때의 경고 — 이름 · 모델 버전 · 남은 점유 수 · 문구 */
	private static final String UNAVAILABLE_STOP = "임베더를 쓸 수 없어 회차를 멈췄다 — {} {}, 기사 {}건: {}";

	private final IntakeQueueRepository queue;

	private final MatchFeaturesRepository features;

	private final AssignmentQueueRepository assignmentQueue;

	private final ArticleDirectory articles;

	private final KeyTermExtractor keyTerms;

	private final TextEmbedding embedding;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	EventIntakeRound(IntakeQueueRepository queue, MatchFeaturesRepository features,
			AssignmentQueueRepository assignmentQueue, ArticleDirectory articles, KeyTermExtractor keyTerms,
			TextEmbedding embedding, TransactionTemplate transaction) {
		this(queue, features, assignmentQueue, articles, keyTerms, embedding, transaction, InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	EventIntakeRound(IntakeQueueRepository queue, MatchFeaturesRepository features,
			AssignmentQueueRepository assignmentQueue, ArticleDirectory articles, KeyTermExtractor keyTerms,
			TextEmbedding embedding, TransactionTemplate transaction, InstantSource clock) {
		this.queue = queue;
		this.features = features;
		this.assignmentQueue = assignmentQueue;
		this.articles = articles;
		this.keyTerms = keyTerms;
		this.embedding = embedding;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 회차 하나를 돈다 — 임베딩은 생성자로 받은 {@link TextEmbedding} 으로 얻는다 */
	public void run() {
		Instant now = this.clock.instant();
		List<PendingArticle> claimed = claim(now);
		if (claimed.isEmpty()) {
			return;
		}
		Map<ArticleId, ArticleContent> contents = this.articles
			.findAll(claimed.stream().map(PendingArticle::articleId).toList());
		List<PendingArticle> present = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		for (PendingArticle claim : claimed) {
			ArticleContent content = contents.get(claim.articleId());
			if (content == null) {
				this.transaction.executeWithoutResult(status -> this.queue.deleteIfLeased(claim.articleId(), token(claim)));
				logger.info("사건 묶기 대기 기사가 지워져 대기에서 뺐다 — 기사 {}", claim.articleId().value());
				continue;
			}
			present.add(claim);
			texts.add(MatchText.compose(content.title(), content.summary()));
		}
		if (present.isEmpty()) {
			return;
		}
		List<List<String>> terms = texts.stream().map(this.keyTerms::extract).toList();
		EmbedderInfo info = this.embedding.info();
		EmbeddingResult result;
		try {
			result = this.embedding.embed(texts);
		}
		catch (RuntimeException ex) {
			stopOnError(present, info, ex.getClass().getName(), ex);
			return;
		}
		switch (result) {
			case Embeddings embeddings -> {
				for (int i = 0; i < present.size(); i++) {
					write(present.get(i), info, embeddings.vectors().get(i), terms.get(i), now);
				}
			}
			case EmbeddingFailure failure -> {
				@Nullable String stop = stopPhrase(failure);
				if (stop != null) {
					stopOnFailure(present, info, stop, failure);
				}
				else if (present.size() == 1) {
					reject(present.getFirst(), failure);
				}
				else {
					isolate(info, present, texts, terms, now);
				}
			}
		}
	}

	/**
	 * 묶음이 입력 거부일 때 기사 하나씩 다시 불러 거부된 기사를 가린다 — 성공은 쓰고 입력 거부는 거부 수를 센다. 그 밖의 실패 · 예외 · 결과
	 * 어긋남이면 그 기사부터 남은 점유를 풀고 회차를 끝낸다(event-R6).
	 */
	private void isolate(EmbedderInfo info, List<PendingArticle> present, List<String> texts, List<List<String>> terms,
			Instant now) {
		for (int i = 0; i < present.size(); i++) {
			PendingArticle claim = present.get(i);
			List<PendingArticle> remaining = present.subList(i, present.size());
			EmbeddingResult result;
			try {
				result = this.embedding.embed(List.of(texts.get(i)));
			}
			catch (RuntimeException ex) {
				stopOnError(remaining, info, ex.getClass().getName(), ex);
				return;
			}
			switch (result) {
				case Embeddings embeddings -> {
					write(claim, info, embeddings.vectors().getFirst(), terms.get(i), now);
				}
				case EmbeddingFailure failure -> {
					@Nullable String stop = stopPhrase(failure);
					if (stop != null) {
						stopOnFailure(remaining, info, stop, failure);
						return;
					}
					reject(claim, failure);
				}
			}
		}
	}

	/** 토큰이 맞아 대기 행을 지웠을 때만 표현을 쓰고 배정 대기에 넣는다 — 표현 행이 이미 있어도 넣는다(event-R6 · R16) */
	private void write(PendingArticle claim, EmbedderInfo info, Embedding embedding, List<String> terms, Instant now) {
		MatchFeatures written = new MatchFeatures(claim.articleId(), info.modelVersion(), embedding, terms, now);
		this.transaction.executeWithoutResult(status -> {
			if (this.queue.deleteIfLeased(claim.articleId(), token(claim))) {
				this.features.insertIfAbsent(written);
				this.assignmentQueue.enqueue(claim.articleId(), now);
			}
		});
	}

	/** 거부 수를 하나 올리고 점유를 푼다 — 상한이면 대기 행을 지운다(event-R6) */
	private void reject(PendingArticle claim, EmbeddingFailure failure) {
		this.transaction.executeWithoutResult(status -> {
			OptionalInt rejections = this.queue.recordRejectionIfLeased(claim.articleId(), token(claim));
			if (rejections.isEmpty()) {
				return;
			}
			int count = rejections.getAsInt();
			if (IntakeSchedule.rejectionsExhausted(count)) {
				this.queue.delete(List.of(claim.articleId()));
				logger.warn("임베더가 기사를 거부해 사건 묶기에서 뺐다 — 기사 {}, 거부 {}회: {}", claim.articleId().value(), count,
						failure.message());
			}
			else {
				logger.info("임베더가 기사를 거부했다 — 기사 {}, 거부 {}회: {}", claim.articleId().value(), count,
						failure.message());
			}
		});
	}

	private List<PendingArticle> claim(Instant now) {
		List<PendingArticle> claimed = this.transaction.execute(status -> {
			List<PendingArticle> leased = new ArrayList<>();
			for (PendingArticle due : this.queue.lockDue(now, IntakeSchedule.BATCH_SIZE)) {
				PendingArticle next = due.claim(UUID.randomUUID(), now);
				this.queue.saveLocked(next);
				leased.add(next);
			}
			return leased;
		});
		return Objects.requireNonNull(claimed, "claimed");
	}

	/**
	 * 실패 갈래마다 회차를 멈출 때의 경고 문구 — 입력 거부는 회차를 멈추지 않아 없다
	 */
	private static @Nullable String stopPhrase(EmbeddingFailure failure) {
		return switch (failure.kind()) {
			case TRANSIENT -> TRANSIENT_STOP;
			case EMBEDDER_UNAVAILABLE -> UNAVAILABLE_STOP;
			case INPUT_REJECTED -> null;
		};
	}

	/**
	 * 예상한 실패(일시 · 사용 불가) — 남은 점유를 풀고 경고를 남기고 회차를 끝낸다. 다음 회차가 다시 점유하고, 거부 수는 그대로다.
	 * @param phrase 경고 문구 — {@link #TRANSIENT_STOP} · {@link #UNAVAILABLE_STOP}
	 */
	private void stopOnFailure(List<PendingArticle> remaining, EmbedderInfo info, String phrase,
			EmbeddingFailure failure) {
		release(remaining);
		logger.warn(phrase, info.name(), info.modelVersion(), remaining.size(), failure.message());
	}

	/** 예상 못한 예외 · 결과 어긋남 — 남은 점유를 풀고 회차를 끝낸다. 사유는 예외 클래스 이름이다 — 결과 어긋남도 관문의 예외로 온다 */
	private void stopOnError(List<PendingArticle> remaining, EmbedderInfo info, String reason, RuntimeException cause) {
		release(remaining);
		logger.error("임베더가 실패해 회차를 멈췄다 — {} {}, 기사 {}건: {}", info.name(), info.modelVersion(), remaining.size(),
				reason, cause);
	}

	private void release(List<PendingArticle> claims) {
		for (PendingArticle claim : claims) {
			this.transaction.executeWithoutResult(status -> this.queue.releaseIfLeased(claim.articleId(), token(claim)));
		}
	}

	private static UUID token(PendingArticle claim) {
		return Objects.requireNonNull(claim.lease(), "lease").token();
	}

}
