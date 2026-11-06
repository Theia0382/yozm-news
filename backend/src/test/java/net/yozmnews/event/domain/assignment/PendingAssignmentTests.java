package net.yozmnews.event.domain.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.intake.IntakeLease;
import org.junit.jupiter.api.Test;

/**
 * 배정 대기 행의 전이 — 점유는 20분, 일시 실패는 시도 수를 늘려 미루고 점유를 풀며, 다섯 번째에 상한이다.
 * <p>
 * 지키는 규칙: event-R16
 */
class PendingAssignmentTests {

	private static final UUID TOKEN = UUID.fromString("00000000-0000-4000-8000-000000000002");

	private static final Instant ENQUEUED = Instant.parse("2026-11-06T00:00:00Z");

	@Test
	void aClaimLeasesTheRowForTwentyMinutes() {
		PendingAssignment pending = new PendingAssignment(new ArticleId(7), ENQUEUED, 2, ENQUEUED, null);

		assertThat(pending.claim(TOKEN, Instant.parse("2026-11-06T01:00:00Z")))
			.isEqualTo(new PendingAssignment(new ArticleId(7), ENQUEUED, 2, ENQUEUED,
					new IntakeLease(TOKEN, Instant.parse("2026-11-06T01:20:00Z"))));
	}

	@Test
	void aTransientFailureCountsDefersAndReleases() {
		PendingAssignment claimed = new PendingAssignment(new ArticleId(7), ENQUEUED, 0, ENQUEUED,
				new IntakeLease(TOKEN, Instant.parse("2026-11-06T01:20:00Z")));

		PendingAssignment failed = claimed.afterTransient(Instant.parse("2026-11-06T01:05:00Z"));

		assertThat(failed).isEqualTo(new PendingAssignment(new ArticleId(7), ENQUEUED, 1,
				Instant.parse("2026-11-06T01:06:00Z"), null));
		assertThat(failed.exhausted()).isFalse();
	}

	@Test
	void theFifthTransientFailureIsTheLimit() {
		PendingAssignment fourth = new PendingAssignment(new ArticleId(7), ENQUEUED, 3, ENQUEUED, null)
			.afterTransient(ENQUEUED);
		assertThat(fourth.attempts()).isEqualTo(4);
		assertThat(fourth.exhausted()).isFalse();

		PendingAssignment fifth = fourth.afterTransient(ENQUEUED);
		assertThat(fifth.attempts()).isEqualTo(5);
		assertThat(fifth.exhausted()).isTrue();
	}

	@Test
	void negativeAttemptsAreRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new PendingAssignment(new ArticleId(7), ENQUEUED, -1, ENQUEUED, null));
	}

}
