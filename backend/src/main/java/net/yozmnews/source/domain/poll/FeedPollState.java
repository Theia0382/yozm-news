package net.yozmnews.source.domain.poll;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.domain.fetch.FailurePermanence;
import net.yozmnews.source.domain.fetch.FetchResult;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.profile.PollInterval;
import org.jspecify.annotations.Nullable;

/**
 * 피드 하나의 수집 상태 — docs/specs/source.md §4 · source-R15 · source-R16 · source-R19 ~ source-R27.
 *
 * @param nextPollAt 다음에 대상이 되는 시각
 * @param lease 점유 — 없으면 null
 * @param fetchState 다음 요청에 보낼 조건부 요청 값
 * @param lastPolledAt 마지막으로 수집을 마친 시각(성공 · 변경 없음 · 실패) — 없으면 null
 * @param lastSuccessAt 마지막 성공 · 변경 없음 시각 — 없으면 null
 * @param lastNewItemAt 새 항목이 있던 마지막 수집의 시각 — 없으면 null
 * @param latestPublishedAt 지금까지 새 항목의 발행 시각 가운데 가장 늦은 것 — 발행 시각을 추정한 항목은 받은 시각으로 센다. 없으면 null
 * @param consecutivePermanentFailures 연속 실패 가운데 영구 실패의 수 — 성공 · 변경 없음에 0
 * @param lastError 마지막 실패 — 없으면 null
 * @param movedTo 마지막 결과가 거친 영구 이동의 대상 — 없으면 null
 * @param lastOverflowAt 마지막 넘침 의심 시각 — 없으면 null
 * @param previousItemKeys 직전 성공 수집의 항목 키 — 아직 성공한 수집이 없으면 null
 */
public record FeedPollState(FeedId feedId, Instant nextPollAt, @Nullable Lease lease, FetchState fetchState,
		@Nullable Instant lastPolledAt, @Nullable Instant lastSuccessAt, @Nullable Instant lastNewItemAt,
		@Nullable Instant latestPublishedAt, int consecutiveFailures, int consecutivePermanentFailures,
		@Nullable LastError lastError, @Nullable URI movedTo, @Nullable Instant lastOverflowAt, int overflowCount,
		@Nullable List<ItemKey> previousItemKeys) {

	public FeedPollState {
		Objects.requireNonNull(feedId, "feedId");
		Objects.requireNonNull(nextPollAt, "nextPollAt");
		Objects.requireNonNull(fetchState, "fetchState");
		if (consecutiveFailures < 0) {
			throw new IllegalArgumentException("연속 실패 수는 0 이상이다: " + consecutiveFailures);
		}
		if (consecutivePermanentFailures < 0 || consecutivePermanentFailures > consecutiveFailures) {
			throw new IllegalArgumentException("연속 영구 실패 수는 0 이상 연속 실패 수 이하다: " + consecutivePermanentFailures
					+ " / " + consecutiveFailures);
		}
		if (overflowCount < 0) {
			throw new IllegalArgumentException("넘침 의심 횟수는 0 이상이다: " + overflowCount);
		}
		previousItemKeys = (previousItemKeys != null) ? List.copyOf(previousItemKeys) : null;
	}

	/** 새 피드의 수집 상태를 만든다 — 곧바로 대상이 되지만 켜지기 전에는 대상 조회가 거른다 */
	public static FeedPollState initial(FeedId feedId, Instant now) {
		return new FeedPollState(feedId, now, null, FetchState.NONE, null, null, null, null, 0, 0, null, null, null, 0,
				null);
	}

	/**
	 * 점유한다. 만료된 점유(now 가 nextPollAt 이상)는 새 점유로 덮어쓴다 — 대상 조회가 고른 행에는 만료된 점유가 남아 있을 수 있다.
	 * @throws IllegalStateException 유효한 점유가 있을 때
	 */
	public FeedPollState claim(Lease lease, Instant now) {
		Objects.requireNonNull(lease, "lease");
		Objects.requireNonNull(now, "now");
		if (this.lease != null && now.isBefore(this.nextPollAt)) {
			throw new IllegalStateException("유효한 점유가 있다: " + this.feedId);
		}
		return new FeedPollState(this.feedId, lease.scheduledAt().plus(PollSchedule.LEASE), lease, this.fetchState,
				this.lastPolledAt, this.lastSuccessAt, this.lastNewItemAt, this.latestPublishedAt, this.consecutiveFailures,
				this.consecutivePermanentFailures, this.lastError, this.movedTo, this.lastOverflowAt, this.overflowCount,
				this.previousItemKeys);
	}

	/**
	 * 수집 결과를 반영하고 점유를 푼다.
	 * @param interval 이 피드의 실제 주기
	 * @throws IllegalStateException 점유가 없을 때
	 */
	public PollOutcome complete(FetchResult result, PollInterval interval) {
		Objects.requireNonNull(result, "result");
		Objects.requireNonNull(interval, "interval");
		if (this.lease == null) {
			throw new IllegalStateException("점유 없이 수집 결과를 반영할 수 없다: " + this.feedId);
		}
		return switch (result) {
			case FetchResult.Fetched fetched -> fetched(fetched, interval);
			case FetchResult.NotModified notModified -> {
				Instant at = notModified.fetchedAt();
				FeedPollState next = new FeedPollState(this.feedId, PollSchedule.afterSuccess(at, interval), null,
						notModified.state(), at, at, this.lastNewItemAt, this.latestPublishedAt, 0, 0, this.lastError,
						notModified.movedTo(), this.lastOverflowAt, this.overflowCount, this.previousItemKeys);
				yield new PollOutcome(next, List.of(), false, movedToChanged(notModified.movedTo()));
			}
			case FetchResult.Failed failed -> {
				Instant at = failed.attemptedAt();
				FailurePermanence permanence = failed.failure().permanence();
				int failures = this.consecutiveFailures + 1;
				int permanentFailures = this.consecutivePermanentFailures
						+ ((permanence == FailurePermanence.PERMANENT) ? 1 : 0);
				Instant nextPollAt = PollSchedule.afterFailure(at, interval, failures, permanence);
				FeedPollState next = new FeedPollState(this.feedId, nextPollAt, null, this.fetchState, at,
						this.lastSuccessAt, this.lastNewItemAt, this.latestPublishedAt, failures, permanentFailures,
						LastError.of(failed.failure(), at), this.movedTo, this.lastOverflowAt, this.overflowCount,
						this.previousItemKeys);
				yield new PollOutcome(next, List.of(), false, false);
			}
		};
	}

	/** 프로필이나 그 안의 피드를 얻지 못한 피드를 미룬다 — source-R19 */
	public FeedPollState deferForUnavailableProfile(Instant now) {
		return new FeedPollState(this.feedId, now.plus(PollSchedule.PROFILE_RECHECK), null, this.fetchState,
				this.lastPolledAt, this.lastSuccessAt, this.lastNewItemAt, this.latestPublishedAt, this.consecutiveFailures,
				this.consecutivePermanentFailures, this.lastError, this.movedTo, this.lastOverflowAt, this.overflowCount,
				this.previousItemKeys);
	}

	private PollOutcome fetched(FetchResult.Fetched fetched, PollInterval interval) {
		Instant at = fetched.fetchedAt();
		ItemComparison comparison = ItemComparison.compare(this.previousItemKeys, fetched.normalization().articles());
		boolean overflow = comparison.overflowSuspected();
		FeedPollState next = new FeedPollState(this.feedId, PollSchedule.afterSuccess(at, interval), null,
				fetched.state(), at, at, comparison.newArticles().isEmpty() ? this.lastNewItemAt : at,
				latest(this.latestPublishedAt, comparison.newArticles()), 0, 0, this.lastError, fetched.movedTo(),
				overflow ? at : this.lastOverflowAt, overflow ? this.overflowCount + 1 : this.overflowCount,
				comparison.currentKeys());
		return new PollOutcome(next, comparison.newArticles(), overflow, movedToChanged(fetched.movedTo()));
	}

	/**
	 * 직전 값과 새 항목의 발행 시각 가운데 가장 늦은 것을 고른다. 새 항목이 없으면 직전 값이다.
	 * <p>
	 * 발행 시각을 추정한 항목의 발행 시각은 이미 받은 시각이라(source-R40) 그대로 센다.
	 */
	private static @Nullable Instant latest(@Nullable Instant previous, List<FetchedArticle> newArticles) {
		Instant latest = previous;
		for (FetchedArticle article : newArticles) {
			if (latest == null || article.publishedAt().isAfter(latest)) {
				latest = article.publishedAt();
			}
		}
		return latest;
	}

	private boolean movedToChanged(@Nullable URI movedTo) {
		return movedTo != null && !movedTo.equals(this.movedTo);
	}

}
