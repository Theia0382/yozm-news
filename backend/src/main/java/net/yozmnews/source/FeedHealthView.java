package net.yozmnews.source;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 피드의 수집 상태 — 운영 목록의 피드 하나. docs/specs/source.md §3.
 * <p>
 * 상태는 피드 건강 검사가 저장한 값이고, {@code statusChangedAt} 은 그것을 마지막으로 바꾼 시각이다 — 바꾼 적이 없으면 null
 * (source-R47 · source-R49). 마지막 실패는 성공한 뒤에도 남는다(source-R26).
 *
 * @param lastSuccessAt 마지막 성공 · 변경 없음 시각 — 없으면 null
 * @param consecutivePermanentFailures 연속 실패 가운데 영구 실패의 수 — 연속 실패 수를 넘지 않는다
 * @param lastNewItemAt 새 항목이 있던 마지막 수집의 시각 — 없으면 null
 * @param latestPublishedAt 새 항목의 가장 늦은 발행 시각(추정이면 받은 시각) — 없으면 null
 */
public record FeedHealthView(FeedStatus status, @Nullable Instant statusChangedAt, @Nullable Instant lastSuccessAt,
		int consecutiveFailures, int consecutivePermanentFailures, @Nullable LastFailureView lastFailure,
		@Nullable Instant lastNewItemAt, @Nullable Instant latestPublishedAt) {

	public FeedHealthView {
		Objects.requireNonNull(status, "status");
		if (consecutivePermanentFailures < 0 || consecutivePermanentFailures > consecutiveFailures) {
			throw new IllegalArgumentException("영구 실패 수는 0 이상 연속 실패 수 이하다: " + consecutivePermanentFailures
					+ " / " + consecutiveFailures);
		}
	}

}
