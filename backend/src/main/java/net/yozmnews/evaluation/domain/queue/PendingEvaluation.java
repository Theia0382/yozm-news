package net.yozmnews.evaluation.domain.queue;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import net.yozmnews.article.ArticleId;
import org.jspecify.annotations.Nullable;

/**
 * 평가 대기 행 하나와 그 전이 — 점유 · 일시 실패 · 기사 거부 · 점유 해제 · docs/specs/evaluation.md §5.
 *
 * @param eligibleAt 이 시각부터 평가할 수 있다 — 정착 시간 · 재시도 간격이 정한다
 * @param attempts 지금까지의 일시 실패 수
 * @param lease 회차의 점유 — 없으면 null
 * @param failure 실패로 남은 까닭 — 대기 중이면 null. 실패로 남은 행은 점유가 없다
 * @param lastError 마지막 실패의 운영자용 문구 — 없으면 null
 */
public record PendingEvaluation(ArticleId articleId, Instant eligibleAt, int attempts,
		@Nullable EvaluationLease lease, @Nullable QueueFailure failure, @Nullable String lastError) {

	/**
	 * @throws IllegalArgumentException 시도 수가 음수이거나 실패로 남은 행에 점유가 있을 때
	 */
	public PendingEvaluation {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(eligibleAt, "eligibleAt");
		if (attempts < 0) {
			throw new IllegalArgumentException("시도 수는 음수가 아니다: " + attempts);
		}
		if (failure != null && lease != null) {
			throw new IllegalArgumentException("실패로 남은 대기 행은 점유가 없다");
		}
	}

	/**
	 * 회차가 점유한다 — 점유 기간은 {@link EvaluationSchedule#LEASE}.
	 * @throws IllegalStateException 실패로 남은 행일 때
	 */
	public PendingEvaluation claim(UUID token, Instant now) {
		requireWaiting();
		return new PendingEvaluation(this.articleId, this.eligibleAt, this.attempts,
				new EvaluationLease(token, now.plus(EvaluationSchedule.LEASE)), null, this.lastError);
	}

	/**
	 * 일시 실패 — 시도 수를 하나 늘리고, 상한이면 실패로 남기고, 아니면 재시도 간격 뒤로 미룬다. 점유를 푼다.
	 * @throws IllegalStateException 실패로 남은 행일 때
	 */
	public PendingEvaluation afterTransient(String message, Instant now) {
		requireWaiting();
		int next = this.attempts + 1;
		if (next >= EvaluationSchedule.MAX_ATTEMPTS) {
			return new PendingEvaluation(this.articleId, this.eligibleAt, next, null, QueueFailure.RETRIES_EXHAUSTED,
					message);
		}
		return new PendingEvaluation(this.articleId, now.plus(EvaluationSchedule.retryDelay(next)), next, null, null,
				message);
	}

	/**
	 * 기사 거부 — 실패로 남긴다. 점유를 푼다.
	 * @throws IllegalStateException 실패로 남은 행일 때
	 */
	public PendingEvaluation afterRejected(String message) {
		requireWaiting();
		return new PendingEvaluation(this.articleId, this.eligibleAt, this.attempts, null,
				QueueFailure.ARTICLE_REJECTED, message);
	}

	/** 점유만 푼다 — 시도 수 · 평가 가능 시각은 그대로(회차를 멈출 때) */
	public PendingEvaluation released() {
		return new PendingEvaluation(this.articleId, this.eligibleAt, this.attempts, null, this.failure,
				this.lastError);
	}

	private void requireWaiting() {
		if (this.failure != null) {
			throw new IllegalStateException("실패로 남은 대기 행이다: " + this.articleId.value());
		}
	}

}
