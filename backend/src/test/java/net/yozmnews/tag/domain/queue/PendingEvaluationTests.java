package net.yozmnews.tag.domain.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import net.yozmnews.article.ArticleId;
import org.junit.jupiter.api.Test;

/**
 * 평가 대기의 일정과 전이 — 정착 시간 · 점유 · 일시 실패의 재시도 간격과 상한 · 기사 거부 · 점유 해제.
 * <p>
 * 지키는 규칙: tag-R18 · tag-R20
 */
class PendingEvaluationTests {

	private static final ArticleId ARTICLE = new ArticleId(7);

	private static final Instant T = Instant.parse("2026-11-02T00:00:00Z");

	private static final UUID TOKEN = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Test
	void anEnqueuedArticleBecomesEligibleThirtyMinutesLater() {
		assertThat(EvaluationSchedule.eligibleAfterEnqueue(T)).isEqualTo(Instant.parse("2026-11-02T00:30:00Z"));
	}

	@Test
	void aClaimLeasesTheRowForThirtyMinutes() {
		PendingEvaluation claimed = waiting(0).claim(TOKEN, T);

		assertThat(claimed.lease()).isEqualTo(new EvaluationLease(TOKEN, Instant.parse("2026-11-02T00:30:00Z")));
		assertThat(claimed.attempts()).isZero();
	}

	@Test
	void transientFailuresBackOffOneTwoFourEightMinutesAndTheFifthFails() {
		PendingEvaluation row = waiting(0);

		PendingEvaluation first = row.claim(TOKEN, T).afterTransient("시간 초과", T);
		PendingEvaluation second = first.claim(TOKEN, T).afterTransient("시간 초과", T);
		PendingEvaluation third = second.claim(TOKEN, T).afterTransient("시간 초과", T);
		PendingEvaluation fourth = third.claim(TOKEN, T).afterTransient("시간 초과", T);
		PendingEvaluation fifth = fourth.claim(TOKEN, T).afterTransient("서버 오류", T);

		assertThat(first).isEqualTo(new PendingEvaluation(ARTICLE, Instant.parse("2026-11-02T00:01:00Z"), 1, null, null,
				"시간 초과"));
		assertThat(second.eligibleAt()).isEqualTo(Instant.parse("2026-11-02T00:02:00Z"));
		assertThat(third.eligibleAt()).isEqualTo(Instant.parse("2026-11-02T00:04:00Z"));
		assertThat(fourth).isEqualTo(new PendingEvaluation(ARTICLE, Instant.parse("2026-11-02T00:08:00Z"), 4, null,
				null, "시간 초과"));
		assertThat(fifth).isEqualTo(new PendingEvaluation(ARTICLE, Instant.parse("2026-11-02T00:08:00Z"), 5, null,
				QueueFailure.RETRIES_EXHAUSTED, "서버 오류"));
	}

	@Test
	void theRetryDelayStopsAtOneHourWithoutOverflowing() {
		assertThat(EvaluationSchedule.retryDelay(6)).isEqualTo(Duration.ofMinutes(32));
		assertThat(EvaluationSchedule.retryDelay(7)).isEqualTo(Duration.ofHours(1));
		assertThat(EvaluationSchedule.retryDelay(8)).isEqualTo(Duration.ofHours(1));
		assertThat(EvaluationSchedule.retryDelay(Integer.MAX_VALUE)).isEqualTo(Duration.ofHours(1));
		assertThatIllegalArgumentException().isThrownBy(() -> EvaluationSchedule.retryDelay(0));
	}

	@Test
	void aRejectedArticleFailsAtOnceKeepingItsAttempts() {
		PendingEvaluation rejected = waiting(2).claim(TOKEN, T).afterRejected("입력 거부");

		assertThat(rejected).isEqualTo(new PendingEvaluation(ARTICLE, T, 2, null, QueueFailure.ARTICLE_REJECTED,
				"입력 거부"));
	}

	@Test
	void releasingKeepsTheAttemptsAndTheEligibleTime() {
		PendingEvaluation released = waiting(3).claim(TOKEN, T.plusSeconds(90)).released();

		assertThat(released).isEqualTo(waiting(3));
	}

	@Test
	void aFailedRowCannotBeClaimedOrLeased() {
		PendingEvaluation failed = new PendingEvaluation(ARTICLE, T, 5, null, QueueFailure.RETRIES_EXHAUSTED, "x");

		assertThatIllegalStateException().isThrownBy(() -> failed.claim(TOKEN, T));
		assertThatIllegalArgumentException().isThrownBy(() -> new PendingEvaluation(ARTICLE, T, 5,
				new EvaluationLease(TOKEN, T), QueueFailure.RETRIES_EXHAUSTED, "x"));
	}

	private static PendingEvaluation waiting(int attempts) {
		return new PendingEvaluation(ARTICLE, T, attempts, null, null, null);
	}

}
