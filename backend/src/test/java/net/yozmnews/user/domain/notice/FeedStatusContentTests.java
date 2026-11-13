package net.yozmnews.user.domain.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.FeedStatus;
import net.yozmnews.source.FeedStatusTransition;
import net.yozmnews.source.FeedStatusesChanged;
import org.junit.jupiter.api.Test;

/**
 * 피드 상태 알림의 모으기 — 기대값은 손으로 쓴 줄이다.
 * <p>
 * 지키는 규칙: user-R23
 */
class FeedStatusContentTests {

	private static final Instant T1 = Instant.parse("2026-11-13T00:00:00Z");

	private static final Instant T2 = Instant.parse("2026-11-13T01:00:00Z");

	private static final Instant T3 = Instant.parse("2026-11-13T02:00:00Z");

	private static final FeedId A = new FeedId(11);

	private static final FeedId B = new FeedId(12);

	private static final Map<FeedId, FeedLabel> LABELS = Map.of(A, new FeedLabel("알림일보", "정치"), B,
			new FeedLabel("알림일보", "경제"));

	@Test
	void aNewNoticeHasALinePerChangedFeedInFeedIdOrder() {
		FeedStatusContent content = FeedStatusContent.first(changed(T1, transition(B, FeedStatus.ACTIVE, FeedStatus.STALE),
				transition(A, FeedStatus.ACTIVE, FeedStatus.DEGRADED)), LABELS);

		assertThat(content.feeds()).containsExactly(
				new FeedStatusEntry(A, "알림일보", "정치", FeedStatus.ACTIVE, FeedStatus.DEGRADED, T1, T1, 1),
				new FeedStatusEntry(B, "알림일보", "경제", FeedStatus.ACTIVE, FeedStatus.STALE, T1, T1, 1));
	}

	@Test
	void aSecondChangeOfTheSameFeedMovesItsLastStatusAndCountsIt() {
		FeedStatusContent first = FeedStatusContent.first(changed(T1, transition(A, FeedStatus.ACTIVE, FeedStatus.DEGRADED)),
				LABELS);

		FeedStatusContent merged = first.merge(changed(T2, transition(A, FeedStatus.DEGRADED, FeedStatus.STALE),
				transition(B, FeedStatus.ACTIVE, FeedStatus.STALE)), LABELS);

		assertThat(merged.feeds()).containsExactly(
				new FeedStatusEntry(A, "알림일보", "정치", FeedStatus.ACTIVE, FeedStatus.STALE, T1, T2, 2),
				new FeedStatusEntry(B, "알림일보", "경제", FeedStatus.ACTIVE, FeedStatus.STALE, T2, T2, 1));
	}

	@Test
	void aFeedThatCameBackKeepsItsLineWithTheSameFirstAndLastStatus() {
		FeedStatusContent content = FeedStatusContent
			.first(changed(T1, transition(A, FeedStatus.ACTIVE, FeedStatus.DEGRADED)), LABELS)
			.merge(changed(T2, transition(A, FeedStatus.DEGRADED, FeedStatus.ACTIVE)), LABELS);

		assertThat(content.feeds()).containsExactly(
				new FeedStatusEntry(A, "알림일보", "정치", FeedStatus.ACTIVE, FeedStatus.ACTIVE, T1, T2, 2));
	}

	@Test
	void aFeedWhoseNameIsNotFoundThisTimeKeepsItsEarlierName() {
		FeedStatusContent content = FeedStatusContent
			.first(changed(T1, transition(A, FeedStatus.ACTIVE, FeedStatus.DEGRADED)), LABELS)
			.merge(changed(T2, transition(A, FeedStatus.DEGRADED, FeedStatus.ACTIVE)), Map.of());

		assertThat(content.feeds()).containsExactly(
				new FeedStatusEntry(A, "알림일보", "정치", FeedStatus.ACTIVE, FeedStatus.ACTIVE, T1, T2, 2));
	}

	@Test
	void aFeedWhoseNameIsFoundAgainTakesTheNewName() {
		FeedStatusContent content = FeedStatusContent
			.first(changed(T1, transition(A, FeedStatus.ACTIVE, FeedStatus.DEGRADED)), LABELS)
			.merge(changed(T2, transition(A, FeedStatus.DEGRADED, FeedStatus.ACTIVE)),
					Map.of(A, new FeedLabel("새이름일보", "사회")));

		assertThat(content.feeds()).containsExactly(
				new FeedStatusEntry(A, "새이름일보", "사회", FeedStatus.ACTIVE, FeedStatus.ACTIVE, T1, T2, 2));
	}

	@Test
	void aFeedFirstSeenWithoutANameHasNoName() {
		FeedStatusContent content = FeedStatusContent
			.first(changed(T1, transition(A, FeedStatus.ACTIVE, FeedStatus.DEGRADED)), Map.of());

		assertThat(content.feeds())
			.containsExactly(new FeedStatusEntry(A, null, null, FeedStatus.ACTIVE, FeedStatus.DEGRADED, T1, T1, 1));
	}

	@Test
	void aRunThatIsNotLaterThanEveryLineIsRejected() {
		FeedStatusContent content = FeedStatusContent
			.first(changed(T2, transition(A, FeedStatus.ACTIVE, FeedStatus.DEGRADED)), LABELS);

		assertThatIllegalArgumentException()
			.isThrownBy(() -> content.merge(changed(T2, transition(B, FeedStatus.ACTIVE, FeedStatus.STALE)), LABELS));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> content.merge(changed(T1, transition(B, FeedStatus.ACTIVE, FeedStatus.STALE)), LABELS));
		assertThat(content.merge(changed(T3, transition(B, FeedStatus.ACTIVE, FeedStatus.STALE)), LABELS).feeds())
			.hasSize(2);
	}

	@Test
	void aRunNamingAFeedTwiceIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> FeedStatusContent.first(changed(T1,
				transition(A, FeedStatus.ACTIVE, FeedStatus.DEGRADED), transition(A, FeedStatus.DEGRADED, FeedStatus.STALE)),
				LABELS));
	}

	@Test
	void linesMustBeInFeedIdOrderWithoutRepeats() {
		FeedStatusEntry a = new FeedStatusEntry(A, null, null, FeedStatus.ACTIVE, FeedStatus.DEGRADED, T1, T1, 1);
		FeedStatusEntry b = new FeedStatusEntry(B, null, null, FeedStatus.ACTIVE, FeedStatus.DEGRADED, T1, T1, 1);

		assertThatIllegalArgumentException().isThrownBy(() -> new FeedStatusContent(List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new FeedStatusContent(List.of(b, a)));
		assertThatIllegalArgumentException().isThrownBy(() -> new FeedStatusContent(List.of(a, a)));
		assertThat(new FeedStatusContent(List.of(a, b)).kind()).isEqualTo(NoticeKind.FEED_STATUS);
	}

	@Test
	void aLineCountsAtLeastOneChangeAndASingleChangeMovesTheStatus() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FeedStatusEntry(A, null, null, FeedStatus.ACTIVE, FeedStatus.DEGRADED, T1, T1, 0));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FeedStatusEntry(A, null, null, FeedStatus.ACTIVE, FeedStatus.ACTIVE, T1, T1, 1));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new FeedStatusEntry(A, null, null, FeedStatus.ACTIVE, FeedStatus.ACTIVE, T2, T1, 2));
	}

	private static FeedStatusesChanged changed(Instant at, FeedStatusTransition... transitions) {
		return new FeedStatusesChanged(at, List.of(transitions));
	}

	private static FeedStatusTransition transition(FeedId feed, FeedStatus from, FeedStatus to) {
		return new FeedStatusTransition(feed, from, to);
	}

}
