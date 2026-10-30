package net.yozmnews.source.domain.poll;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * 피드 상태 판정의 규칙과 기준값 — 정본 · docs/specs/source.md §4 · source-R30 · source-R31 · source-R47.
 * <p>
 * 값은 측정 전 목표이고 운영 데이터로 다시 판단한다.
 */
public final class FeedStatusRule {

	/** 이만큼 연속 실패하면 DEGRADED */
	public static final int DEGRADED_AFTER_FAILURES = 5;

	/** 연속 실패 가운데 영구 실패가 이만큼이면 DEGRADED */
	public static final int DEGRADED_AFTER_PERMANENT_FAILURES = 2;

	/** 새 기사가 이만큼 없으면 STALE */
	public static final Duration STALE_AFTER = Duration.ofDays(7);

	private FeedStatusRule() {}

	/**
	 * 수집 상태와 지금 시각으로 상태를 판정한다.
	 * <p>
	 * 계속 실패하는지를 먼저 본다. 성공한 적이 있는 피드는 새 항목이 한 번도 없었거나, 마지막 새 항목 또는 새 항목의 가장 늦은 발행
	 * 시각이 무소식 기준만큼 지났으면 오래 새 기사가 없는 것이다. 성공은 계속 실패하는 상태만 풀 뿐이다.
	 */
	public static FeedStatus judge(FeedPollState state, Instant now) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(now, "now");
		if (state.consecutiveFailures() >= DEGRADED_AFTER_FAILURES
				|| state.consecutivePermanentFailures() >= DEGRADED_AFTER_PERMANENT_FAILURES) {
			return FeedStatus.DEGRADED;
		}
		if (state.lastSuccessAt() != null && (state.lastNewItemAt() == null || quiet(state.lastNewItemAt(), now)
				|| quiet(state.latestPublishedAt(), now))) {
			return FeedStatus.STALE;
		}
		return FeedStatus.ACTIVE;
	}

	/**
	 * 저장된 상태에서 바뀌는지 본다.
	 * @return 새 상태 — 판정이 저장된 상태와 같으면 비어 있다
	 */
	public static Optional<FeedStatus> transition(FeedStatus current, FeedPollState state, Instant now) {
		Objects.requireNonNull(current, "current");
		FeedStatus next = judge(state, now);
		return (next == current) ? Optional.empty() : Optional.of(next);
	}

	private static boolean quiet(@Nullable Instant since, Instant now) {
		return since != null && Duration.between(since, now).compareTo(STALE_AFTER) >= 0;
	}

}
