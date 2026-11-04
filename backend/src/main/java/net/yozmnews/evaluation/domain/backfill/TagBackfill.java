package net.yozmnews.evaluation.domain.backfill;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import net.yozmnews.evaluation.TagBackfillState;
import org.jspecify.annotations.Nullable;

/**
 * 빠진 태그 채움 하나 — 채움 행과 그 점유 · docs/specs/evaluation.md §4.
 * <p>
 * 진행(커서 · 버전별 건수)과 끝냄은 점유 토큰을 조건으로 한 SQL 갱신으로 쓴다 — 저장소 {@link TagBackfillRepository}.
 *
 * @param from 덮는 평가 시각의 시작 — 포함
 * @param to 덮는 평가 시각의 끝 — 제외, 요청 시각
 * @param cursor 처리한 마지막 평가 ID — 처음은 0
 */
public record TagBackfill(long id, int days, Instant from, Instant to, Instant eligibleAt, long cursor,
		@Nullable UUID leaseToken, @Nullable Instant leaseExpiresAt, @Nullable Instant startedAt,
		@Nullable Instant finishedAt) {

	/**
	 * @throws IllegalArgumentException 점유 토큰과 만료가 함께 있거나 함께 없지 않을 때, 끝난 채움에 점유가 있을 때
	 */
	public TagBackfill {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		Objects.requireNonNull(eligibleAt, "eligibleAt");
		if ((leaseToken == null) != (leaseExpiresAt == null)) {
			throw new IllegalArgumentException("점유 토큰과 만료는 함께 있거나 함께 없다");
		}
		if (finishedAt != null && leaseToken != null) {
			throw new IllegalArgumentException("끝난 채움은 점유가 없다");
		}
	}

	/**
	 * 회차가 점유한다 — 점유 기간은 {@link BackfillSchedule#LEASE}, 처음 점유면 시작 시각을 쓴다.
	 * @throws IllegalStateException 끝난 채움일 때
	 */
	public TagBackfill claim(UUID token, Instant now) {
		Objects.requireNonNull(token, "token");
		Objects.requireNonNull(now, "now");
		if (this.finishedAt != null) {
			throw new IllegalStateException("끝난 채움이다: " + this.id);
		}
		return new TagBackfill(this.id, this.days, this.from, this.to, this.eligibleAt, this.cursor, token,
				BackfillSchedule.leaseUntil(now), (this.startedAt != null) ? this.startedAt : now, null);
	}

	/** 상태 — 끝 시각이 있으면 끝남, 시작 시각만 있으면 진행 중, 둘 다 없으면 대기 */
	public TagBackfillState state() {
		if (this.finishedAt != null) {
			return TagBackfillState.DONE;
		}
		return (this.startedAt != null) ? TagBackfillState.RUNNING : TagBackfillState.WAITING;
	}

}
