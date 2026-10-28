package net.yozmnews.source.domain.poll;

import static net.yozmnews.source.domain.poll.PollFixtures.article;
import static net.yozmnews.source.domain.poll.PollFixtures.keys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.domain.feed.FeedNormalization;
import net.yozmnews.source.domain.fetch.FetchFailure;
import net.yozmnews.source.domain.fetch.FetchFailureReason;
import net.yozmnews.source.domain.fetch.FetchResult;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.profile.PollInterval;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * 수집 상태 전이 — 점유하고, 결과(성공 · 변경 없음 · 실패)마다 정해진 값만 바꾸고 점유를 푼다. 기대값은 결과 시각과 주기 15분에서
 * 손으로 센 시각이다.
 */
class FeedPollStateTests {

	private static final FeedId FEED = new FeedId(7);

	private static final PollInterval INTERVAL = new PollInterval(Duration.ofMinutes(15));

	private static final Instant SCHEDULED = Instant.parse("2026-10-28T10:00:10Z");

	private static final Instant DONE = Instant.parse("2026-10-28T10:00:12Z");

	private static final Instant EARLIER = Instant.parse("2026-10-27T08:00:00Z");

	private static final FetchState OLD_STATE = new FetchState("\"old\"", null);

	private static final FetchState NEW_STATE = new FetchState("\"new\"", "Wed, 28 Oct 2026 10:00:00 GMT");

	private static final LastError OLD_ERROR = new LastError(FetchFailureReason.TIMEOUT, "수집 시간 상한을 넘었다", null,
			EARLIER);

	private static final URI MOVED = URI.create("https://news.example/rss/new");

	@Test
	void startsDueImmediatelyWithNothingRecorded() {
		Instant now = Instant.parse("2026-10-28T09:00:00Z");

		assertThat(FeedPollState.initial(FEED, now)).isEqualTo(
				new FeedPollState(FEED, now, null, FetchState.NONE, null, null, null, 0, null, null, null, 0, null));
	}

	@Test
	void claimingHoldsTheFeedForTenMinutesFromTheScheduledTime() {
		Lease lease = new Lease(UUID.randomUUID(), SCHEDULED);

		FeedPollState claimed = FeedPollState.initial(FEED, EARLIER).claim(lease, Instant.parse("2026-10-28T10:00:00Z"));

		assertThat(claimed.lease()).isEqualTo(lease);
		assertThat(claimed.nextPollAt()).isEqualTo(Instant.parse("2026-10-28T10:10:10Z"));
	}

	@Test
	void aValidLeaseCannotBeTakenButAnExpiredOneCan() {
		FeedPollState claimed = FeedPollState.initial(FEED, EARLIER)
			.claim(new Lease(UUID.randomUUID(), SCHEDULED), SCHEDULED);
		Lease next = new Lease(UUID.randomUUID(), Instant.parse("2026-10-28T10:10:10Z"));

		assertThatIllegalStateException().isThrownBy(() -> claimed.claim(next, Instant.parse("2026-10-28T10:10:09Z")));
		assertThat(claimed.claim(next, Instant.parse("2026-10-28T10:10:10Z")).lease()).isEqualTo(next);
	}

	@Test
	void cannotCompleteWithoutALease() {
		assertThatIllegalStateException().isThrownBy(() -> FeedPollState.initial(FEED, EARLIER)
			.complete(new FetchResult.NotModified(NEW_STATE, DONE, null), INTERVAL));
	}

	@Test
	void aFirstSuccessfulFetchRecordsEverythingAndPublishesAllItems() {
		FetchedArticle a = article("a");
		FetchedArticle b = article("b");

		PollOutcome outcome = leased(null, 0, null).complete(fetched(List.of(a, b), null), INTERVAL);

		assertThat(outcome.newArticles()).containsExactly(a, b);
		assertThat(outcome.overflowSuspected()).isFalse();
		assertThat(outcome.movedToChanged()).isFalse();
		assertThat(outcome.state()).isEqualTo(new FeedPollState(FEED, Instant.parse("2026-10-28T10:15:12Z"), null,
				NEW_STATE, DONE, DONE, DONE, 0, OLD_ERROR, null, EARLIER, 2, keys("g:a", "g:b")));
	}

	@Test
	void aFetchWithoutNewItemsKeepsTheLastNewItemTime() {
		PollOutcome outcome = leased(keys("g:a"), 0, null).complete(fetched(List.of(article("a")), null),
				INTERVAL);

		assertThat(outcome.newArticles()).isEmpty();
		assertThat(outcome.state().lastNewItemAt()).isEqualTo(EARLIER);
		assertThat(outcome.state().lastSuccessAt()).isEqualTo(DONE);
	}

	@Test
	void aFetchSharingNoItemWithThePreviousOneCountsAsSuspectedOverflow() {
		PollOutcome outcome = leased(keys("g:a"), 0, null).complete(fetched(List.of(article("c")), null),
				INTERVAL);

		assertThat(outcome.overflowSuspected()).isTrue();
		assertThat(outcome.state().lastOverflowAt()).isEqualTo(DONE);
		assertThat(outcome.state().overflowCount()).isEqualTo(3);
	}

	@Test
	void aSuccessfulFetchResetsFailuresAndReplacesTheMovedAddress() {
		PollOutcome cleared = leased(keys("g:a"), 4, MOVED).complete(fetched(List.of(article("a")), null),
				INTERVAL);
		PollOutcome moved = leased(keys("g:a"), 0, null).complete(fetched(List.of(article("a")), MOVED),
				INTERVAL);
		PollOutcome same = leased(keys("g:a"), 0, MOVED).complete(fetched(List.of(article("a")), MOVED),
				INTERVAL);

		assertThat(cleared.state().consecutiveFailures()).isZero();
		assertThat(cleared.state().movedTo()).isNull();
		assertThat(cleared.movedToChanged()).isFalse();
		assertThat(moved.state().movedTo()).isEqualTo(MOVED);
		assertThat(moved.movedToChanged()).isTrue();
		assertThat(same.movedToChanged()).isFalse();
	}

	@Test
	void notModifiedIsASuccessThatKeepsTheItemKeys() {
		PollOutcome outcome = leased(keys("g:a"), 3, null)
			.complete(new FetchResult.NotModified(NEW_STATE, DONE, MOVED), INTERVAL);

		assertThat(outcome.newArticles()).isEmpty();
		assertThat(outcome.overflowSuspected()).isFalse();
		assertThat(outcome.movedToChanged()).isTrue();
		assertThat(outcome.state()).isEqualTo(new FeedPollState(FEED, Instant.parse("2026-10-28T10:15:12Z"), null,
				NEW_STATE, DONE, DONE, EARLIER, 0, OLD_ERROR, MOVED, EARLIER, 2, keys("g:a")));
	}

	@Test
	void notModifiedWithoutARedirectClearsTheMovedAddress() {
		PollOutcome outcome = leased(keys("g:a"), 0, MOVED)
			.complete(new FetchResult.NotModified(NEW_STATE, DONE, null), INTERVAL);

		assertThat(outcome.state().movedTo()).isNull();
		assertThat(outcome.movedToChanged()).isFalse();
	}

	@Test
	void aTemporaryFailureBacksOffAndKeepsWhatWasLastReceived() {
		FetchFailure failure = FetchFailure.status(FetchFailureReason.SERVER_ERROR, 503);

		PollOutcome outcome = leased(keys("g:a"), 0, MOVED)
			.complete(new FetchResult.Failed(failure, DONE), INTERVAL);

		assertThat(outcome.newArticles()).isEmpty();
		assertThat(outcome.state()).isEqualTo(new FeedPollState(FEED, Instant.parse("2026-10-28T10:30:12Z"), null,
				OLD_STATE, DONE, EARLIER, EARLIER, 1,
				new LastError(FetchFailureReason.SERVER_ERROR, "서버 오류 응답 (HTTP 503)", 503, DONE), MOVED, EARLIER, 2,
				keys("g:a")));
	}

	@Test
	void aPermanentFailureBacksOffTowardsADay() {
		FetchFailure failure = FetchFailure.status(FetchFailureReason.NOT_FOUND, 404);

		PollOutcome outcome = leased(keys("g:a"), 5, null).complete(new FetchResult.Failed(failure, DONE),
				INTERVAL);

		assertThat(outcome.state().consecutiveFailures()).isEqualTo(6);
		assertThat(outcome.state().nextPollAt()).isEqualTo(Instant.parse("2026-10-29T02:00:12Z"));
	}

	@Test
	void deferringForAMissingProfileReleasesTheLeaseForAnHour() {
		FeedPollState state = leased(keys("g:a"), 2, MOVED);

		FeedPollState deferred = state.deferForUnavailableProfile(Instant.parse("2026-10-28T10:00:00Z"));

		assertThat(deferred).isEqualTo(new FeedPollState(FEED, Instant.parse("2026-10-28T11:00:00Z"), null, OLD_STATE,
				EARLIER, EARLIER, EARLIER, 2, OLD_ERROR, MOVED, EARLIER, 2, keys("g:a")));
	}

	/** 점유 중인 상태 — 이전 수집들이 남긴 값은 모두 EARLIER 와 OLD_ 상수다. 넘침 의심은 두 번 있었다 */
	private static FeedPollState leased(@Nullable List<ItemKey> previousKeys, int failures, @Nullable URI movedTo) {
		return new FeedPollState(FEED, Instant.parse("2026-10-28T10:10:10Z"), new Lease(UUID.randomUUID(), SCHEDULED),
				OLD_STATE, EARLIER, EARLIER, EARLIER, failures, OLD_ERROR, movedTo, EARLIER, 2, previousKeys);
	}

	private static FetchResult.Fetched fetched(List<FetchedArticle> articles, @Nullable URI movedTo) {
		return new FetchResult.Fetched(new FeedNormalization.Normalized(articles, List.of(), 0), NEW_STATE, DONE,
				movedTo);
	}

}
