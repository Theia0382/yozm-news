package net.yozmnews.source.domain.poll;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchFailureReason;
import net.yozmnews.source.domain.fetch.FetchFailure;
import net.yozmnews.source.domain.fetch.FetchResult;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.profile.PollInterval;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 피드 상태 판정 — 기대값은 판정 시각 2026-10-30T00:00:00Z 에서 손으로 센 기준 경계다.
 * <p>
 * 지키는 규칙: source-R30 · source-R31 · source-R47
 */
class FeedStatusRuleTests {

	private static final FeedId FEED = new FeedId(7);

	private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");

	/** 하루 전 — 새 기사가 오는 피드의 마지막 성공 · 새 항목 · 발행 시각 */
	private static final Instant RECENT = Instant.parse("2026-10-29T00:00:00Z");

	/** 열흘 전 — 오래 새 기사가 없는 피드의 마지막 새 항목 */
	private static final Instant LONG_AGO = Instant.parse("2026-10-20T00:00:00Z");

	@Test
	void fiveFailuresInARowAreDegradedAndFourAreNot() {
		assertThat(FeedStatusRule.judge(state(5, 0, RECENT, RECENT, RECENT), NOW)).isEqualTo(FeedStatus.DEGRADED);
		assertThat(FeedStatusRule.judge(state(4, 0, RECENT, RECENT, RECENT), NOW)).isEqualTo(FeedStatus.ACTIVE);
	}

	@Test
	void twoPermanentFailuresInARowAreDegradedAndOneIsNot() {
		assertThat(FeedStatusRule.judge(state(2, 2, RECENT, RECENT, RECENT), NOW)).isEqualTo(FeedStatus.DEGRADED);
		assertThat(FeedStatusRule.judge(state(1, 1, RECENT, RECENT, RECENT), NOW)).isEqualTo(FeedStatus.ACTIVE);
	}

	@Test
	void sevenDaysWithoutANewItemAreStaleFromTheExactMoment() {
		assertThat(FeedStatusRule.judge(state(0, 0, RECENT, Instant.parse("2026-10-23T00:00:00Z"), null), NOW))
			.isEqualTo(FeedStatus.STALE);
		assertThat(FeedStatusRule.judge(state(0, 0, RECENT, Instant.parse("2026-10-23T00:00:01Z"), null), NOW))
			.isEqualTo(FeedStatus.ACTIVE);
	}

	@Test
	void newItemsPublishedSevenDaysAgoOrEarlierAreStale() {
		assertThat(FeedStatusRule.judge(state(0, 0, RECENT, RECENT, Instant.parse("2026-10-23T00:00:00Z")), NOW))
			.isEqualTo(FeedStatus.STALE);
		assertThat(FeedStatusRule.judge(state(0, 0, RECENT, RECENT, Instant.parse("2026-10-23T00:00:01Z")), NOW))
			.isEqualTo(FeedStatus.ACTIVE);
	}

	@Test
	void aSucceedingFeedThatNeverHadANewItemIsStale() {
		assertThat(FeedStatusRule.judge(state(0, 0, RECENT, null, null), NOW)).isEqualTo(FeedStatus.STALE);
	}

	@Test
	void aFeedThatNeverSucceededIsActiveUntilItFailsEnough() {
		assertThat(FeedStatusRule.judge(state(4, 0, null, null, null), NOW)).isEqualTo(FeedStatus.ACTIVE);
	}

	@Test
	void failingEnoughWinsOverHavingNoNewItems() {
		assertThat(FeedStatusRule.judge(state(5, 0, RECENT, LONG_AGO, null), NOW)).isEqualTo(FeedStatus.DEGRADED);
	}

	@Test
	void aFeedWithoutNewItemsStaysStaleWhileFailingBelowTheThreshold() {
		assertThat(FeedStatusRule.judge(state(1, 0, RECENT, LONG_AGO, null), NOW)).isEqualTo(FeedStatus.STALE);
	}

	@Test
	void permanentFailuresInARowAreDegradedEvenIfATemporaryOneComesLast() {
		FeedPollState state = failAll(state(0, 0, RECENT, RECENT, RECENT),
				FetchFailure.status(FetchFailureReason.NOT_FOUND, 404), FetchFailure.status(FetchFailureReason.NOT_FOUND, 410),
				FetchFailure.status(FetchFailureReason.SERVER_ERROR, 503));

		assertThat(state.consecutiveFailures()).isEqualTo(3);
		assertThat(state.consecutivePermanentFailures()).isEqualTo(2);
		assertThat(FeedStatusRule.judge(state, NOW)).isEqualTo(FeedStatus.DEGRADED);
	}

	@Test
	void onePermanentFailureFollowedByTemporaryOnesIsNotDegraded() {
		FeedPollState state = failAll(state(0, 0, RECENT, RECENT, RECENT),
				FetchFailure.status(FetchFailureReason.NOT_FOUND, 404),
				FetchFailure.status(FetchFailureReason.SERVER_ERROR, 503),
				FetchFailure.status(FetchFailureReason.SERVER_ERROR, 503));

		assertThat(state.consecutiveFailures()).isEqualTo(3);
		assertThat(state.consecutivePermanentFailures()).isEqualTo(1);
		assertThat(FeedStatusRule.judge(state, NOW)).isEqualTo(FeedStatus.ACTIVE);
	}

	/**
	 * 저장된 상태 × 수집 상태가 만드는 상태의 모든 쌍. 기대가 비어 있으면 바뀌지 않는다. 저장 DEGRADED · 입력 stale 은 성공이
	 * DEGRADED 를 풀어도 새 기사가 여드레 없으면 STALE 이라는 행이다.
	 */
	@ParameterizedTest(name = "{0} + {1} → {2}")
	@CsvSource(nullValues = "none", value = {
		"ACTIVE,   active,   none",
		"ACTIVE,   degraded, DEGRADED",
		"ACTIVE,   stale,    STALE",
		"DEGRADED, active,   ACTIVE",
		"DEGRADED, degraded, none",
		"DEGRADED, stale,    STALE",
		"STALE,    active,   ACTIVE",
		"STALE,    degraded, DEGRADED",
		"STALE,    stale,    none",
	})
	void everyPairOfStoredAndJudgedStatusIsAllowed(FeedStatus stored, String input, @Nullable FeedStatus expected) {
		FeedPollState state = switch (input) {
			case "active" -> state(0, 0, RECENT, RECENT, RECENT);
			case "degraded" -> state(5, 0, RECENT, RECENT, RECENT);
			case "stale" -> state(0, 0, RECENT, Instant.parse("2026-10-22T00:00:00Z"), null);
			default -> throw new IllegalArgumentException(input);
		};

		assertThat(FeedStatusRule.transition(stored, state, NOW)).isEqualTo(Optional.ofNullable(expected));
	}

	private static FeedPollState state(int failures, int permanentFailures, @Nullable Instant lastSuccessAt,
			@Nullable Instant lastNewItemAt, @Nullable Instant latestPublishedAt) {
		return new FeedPollState(FEED, NOW, null, FetchState.NONE, lastSuccessAt, lastSuccessAt, lastNewItemAt,
				latestPublishedAt, failures, permanentFailures, null, null, null, 0, null);
	}

	/** 수집 경로로 실패를 차례로 반영한다 */
	private static FeedPollState failAll(FeedPollState state, FetchFailure... failures) {
		PollInterval interval = new PollInterval(Duration.ofMinutes(15));
		FeedPollState current = state;
		for (FetchFailure failure : failures) {
			current = current.claim(new Lease(UUID.randomUUID(), NOW), NOW)
				.complete(new FetchResult.Failed(failure, NOW), interval)
				.state();
		}
		return current;
	}

}
