package net.yozmnews.feedback.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.feedback.DigestCitations;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.feedback.domain.record.FeedbackAcceptance;
import net.yozmnews.feedback.domain.record.FeedbackRepository;
import net.yozmnews.feedback.domain.record.RemovalFence;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 누르기 · 되돌리기 — docs/specs/feedback.md §4 · feedback-R2 ~ feedback-R4 · feedback-R6.
 * <p>
 * 한 트랜잭션에서 울타리를 공유로 잡고, 기사가 있는지와 인용했는지를 확인해 판정한 뒤, 현재 상태를 쓰고 바뀌었으면 이력 한 줄을 더한다. 기사 확인은
 * 울타리를 잡은 뒤에 한다 — 그래야 지운 기사 따르기가 지나간 뒤 지운 기사를 가리키는 행이 남지 않는다.
 */
@Service
public class FeedbackRecording {

	private final RemovalFence fence;

	private final ArticleDirectory articles;

	private final DigestCitations citations;

	private final FeedbackRepository feedback;

	FeedbackRecording(RemovalFence fence, ArticleDirectory articles, DigestCitations citations,
			FeedbackRepository feedback) {
		this.fence = Objects.requireNonNull(fence, "fence");
		this.articles = Objects.requireNonNull(articles, "articles");
		this.citations = Objects.requireNonNull(citations, "citations");
		this.feedback = Objects.requireNonNull(feedback, "feedback");
	}

	/**
	 * 그 사용자가 그 다이제스트에서 그 기사에 값을 남긴다. 시각은 마이크로초까지 남긴다.
	 * @param digestId 누른 다이제스트의 ID — 1 이상
	 * @throws IllegalArgumentException {@code digestId} 가 1 보다 작을 때
	 */
	@Transactional
	public RecordOutcome record(UserId user, ArticleId article, FeedbackValue value, long digestId, Instant now) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(article, "article");
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(now, "now");
		if (digestId < 1) {
			throw new IllegalArgumentException("다이제스트 ID 는 1 이상이어야 한다: " + digestId);
		}
		this.fence.enterShared();
		boolean exists = this.articles.findAll(List.of(article)).containsKey(article);
		boolean cited = this.citations.cites(user, digestId, article);
		if (!FeedbackAcceptance.accepts(exists, cited)) {
			return new RecordOutcome.NotInDigest();
		}
		Instant at = now.truncatedTo(ChronoUnit.MICROS);
		if (!this.feedback.change(user, article, value, digestId, at)) {
			return new RecordOutcome.Unchanged(value);
		}
		this.feedback.append(user, article, value, digestId, at);
		return new RecordOutcome.Recorded(value);
	}

}
