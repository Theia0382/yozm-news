package net.yozmnews.event.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.assignment.AssignmentQueueRepository;
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import net.yozmnews.event.domain.feature.EmbedderInfo;
import net.yozmnews.event.domain.feature.Embedding;
import net.yozmnews.event.domain.feature.EmbeddingCheck;
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
 * 점유 토큰이 맞아 대기 행을 지웠을 때만 쓰고, 같은 트랜잭션에서 그 기사를 배정 대기에 넣는다(표현 행이 이미 있어도 넣는다). 임베더가
 * 예외를 던지거나 결과가 어긋나면 남은 점유를 풀고 회차를 끝낸다 — 예외를 밖으로 던지지 않는다. 트랜잭션 · 역할 애너테이션이 없다(두 역할
 * 공통).
 */
@Service
public class EventIntakeRound {

	private static final Logger logger = LoggerFactory.getLogger(EventIntakeRound.class);

	private final IntakeQueueRepository queue;

	private final MatchFeaturesRepository features;

	private final AssignmentQueueRepository assignmentQueue;

	private final ArticleDirectory articles;

	private final KeyTermExtractor keyTerms;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	EventIntakeRound(IntakeQueueRepository queue, MatchFeaturesRepository features,
			AssignmentQueueRepository assignmentQueue, ArticleDirectory articles, KeyTermExtractor keyTerms,
			TransactionTemplate transaction) {
		this(queue, features, assignmentQueue, articles, keyTerms, transaction, InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	EventIntakeRound(IntakeQueueRepository queue, MatchFeaturesRepository features,
			AssignmentQueueRepository assignmentQueue, ArticleDirectory articles, KeyTermExtractor keyTerms,
			TransactionTemplate transaction, InstantSource clock) {
		this.queue = queue;
		this.features = features;
		this.assignmentQueue = assignmentQueue;
		this.articles = articles;
		this.keyTerms = keyTerms;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 회차 하나를 돈다 — 임베더는 작업 진입점이 넘긴다 */
	public void run(ArticleEmbedder embedder) {
		Objects.requireNonNull(embedder, "embedder");
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
		EmbedderInfo info = embedder.info();
		List<Embedding> embeddings;
		try {
			embeddings = embedder.embed(texts);
		}
		catch (RuntimeException ex) {
			stop(present, info, ex.toString(), ex);
			return;
		}
		Optional<String> mismatch = EmbeddingCheck.mismatch(info, texts.size(), embeddings);
		if (mismatch.isPresent()) {
			stop(present, info, mismatch.get(), null);
			return;
		}
		for (int i = 0; i < present.size(); i++) {
			PendingArticle claim = present.get(i);
			MatchFeatures written = new MatchFeatures(claim.articleId(), info.modelVersion(), embeddings.get(i),
					terms.get(i), now);
			this.transaction.executeWithoutResult(status -> {
				if (this.queue.deleteIfLeased(claim.articleId(), token(claim))) {
					this.features.insertIfAbsent(written);
					this.assignmentQueue.enqueue(claim.articleId(), now);
				}
			});
		}
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

	/** 남은 점유를 풀고 회차를 끝낸다 — 다음 회차가 다시 점유한다 */
	private void stop(List<PendingArticle> present, EmbedderInfo info, String message,
			@Nullable RuntimeException cause) {
		for (PendingArticle claim : present) {
			this.transaction.executeWithoutResult(status -> this.queue.releaseIfLeased(claim.articleId(), token(claim)));
		}
		logger.error("임베더가 실패해 회차를 멈췄다 — {} {}, 기사 {}건: {}", info.name(), info.modelVersion(), present.size(),
				message, cause);
	}

	private static UUID token(PendingArticle claim) {
		return Objects.requireNonNull(claim.lease(), "lease").token();
	}

}
