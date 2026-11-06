package net.yozmnews.event.domain.assignment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.intake.IntakeLease;
import org.jspecify.annotations.Nullable;

/**
 * 배정 대기 행 하나와 그 전이 — 점유 · 일시 실패 · docs/specs/event.md §4 · event-R16.
 *
 * @param enqueuedAt 대기에 넣은 시각
 * @param attempts 지금까지의 일시 실패 수
 * @param availableAt 이 시각부터 판정할 수 있다 — 재시도 간격이 정한다
 * @param lease 회차의 점유 — 없으면 null
 */
public record PendingAssignment(ArticleId articleId, Instant enqueuedAt, int attempts, Instant availableAt,
		@Nullable IntakeLease lease) {

	/**
	 * @throws IllegalArgumentException 시도 수가 음수일 때
	 */
	public PendingAssignment {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(enqueuedAt, "enqueuedAt");
		Objects.requireNonNull(availableAt, "availableAt");
		if (attempts < 0) {
			throw new IllegalArgumentException("시도 수는 음수가 아니다: " + attempts);
		}
	}

	/** 회차가 점유한다 — 만료는 {@code now} + {@link AssignmentSchedule#LEASE} */
	public PendingAssignment claim(UUID token, Instant now) {
		return new PendingAssignment(this.articleId, this.enqueuedAt, this.attempts, this.availableAt,
				new IntakeLease(token, now.plus(AssignmentSchedule.LEASE)));
	}

	/**
	 * 일시 실패 — 시도 수를 하나 늘리고 재시도 간격 뒤로 미루고 점유를 푼다. 상한에 이르렀는지는 {@link #exhausted()} 가 정한다.
	 */
	public PendingAssignment afterTransient(Instant now) {
		int next = this.attempts + 1;
		return new PendingAssignment(this.articleId, this.enqueuedAt, next, AssignmentSchedule.retryAt(now, next), null);
	}

	/** 일시 실패가 상한({@link AssignmentSchedule#MAX_ATTEMPTS})에 이르렀다 — 판정 실패로 끝낸다 */
	public boolean exhausted() {
		return this.attempts >= AssignmentSchedule.MAX_ATTEMPTS;
	}

}
