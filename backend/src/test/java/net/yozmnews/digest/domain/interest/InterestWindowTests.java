package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.StorylineSubscriptionChange;
import net.yozmnews.user.TopicSubscriptionAction;
import net.yozmnews.user.TopicSubscriptionCause;
import net.yozmnews.user.TopicSubscriptionChange;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * 기준 시각 하나의 관심 — 재료를 창으로 자른다. 기대값은 손으로 계산한 리터럴이다(반감기 30일 · K 3 · γ 1, 30일 전 무게 0.5). 다이제스트의 창 끝은
 * 부르는 시각, 관심사 히스토리의 창 끝은 기준 시각이다. 노출 행은 지금 사건으로 푼 것이고, 몫은 따로 적지 않으면 1 · 다이제스트는 행마다 다르다.
 * <p>
 * 지키는 규칙: digest-R25 · digest-R35
 */
class InterestWindowTests {

	private static final Instant T = Instant.parse("2026-11-17T00:00:00Z");

	private static final Instant MONTH_BEFORE = T.minus(Duration.ofDays(30));

	private static final Instant DAY_AFTER = T.plus(Duration.ofDays(1));

	/** 다이제스트가 부르는 시각 — 기준 시각(기간 끝) 이틀 뒤 */
	private static final Instant NOW = T.plus(Duration.ofDays(2));

	private static final TagSetId TAG_SET = new TagSetId(7);

	private static final Offset<Double> EXACT = within(1e-12);

	@Test
	void theWindowStartsOneHundredTwentyDaysBeforeTheReference() {
		assertThat(InterestWindow.start(T)).isEqualTo(Instant.parse("2026-07-20T00:00:00Z"));
	}

	@Test
	void anEventShownAfterTheReferenceKeepsOnlyItsEarlierExposureInTheHistoryWindow() {
		// 사건 1(a): 30일 전 · 하루 뒤 노출, 좋아요(29일 전)
		InterestRecord record = record(
				List.of(exposure(1, MONTH_BEFORE), exposure(1, DAY_AFTER)), Map.of(event(1), Set.of("a")),
				List.of(feedback(1, FeedbackValue.LIKE, T.minus(Duration.ofDays(29)))), List.of(), List.of(), Map.of());

		// 히스토리: 노출 30일 전(무게 0.5) · 좋아요 → x = 0.5 ÷ 3.5 = 1/7
		assertThat(InterestWindow.levels(record, T, T).level("a")).isCloseTo(0.14285714285714285, EXACT);
		// 다이제스트: 노출 하루 뒤(무게 1) · 좋아요 → x = 1 ÷ 4
		assertThat(InterestWindow.levels(record, T, NOW).level("a")).isCloseTo(0.25, EXACT);
	}

	@Test
	void aReactionAfterTheReferenceIsLeftOutOfTheHistoryWindowButCountsForTheDigest() {
		// 사건 2(b): 30일 전 노출, 좋아요는 기준 시각 하루 뒤
		InterestRecord record = record(List.of(exposure(2, MONTH_BEFORE)), Map.of(event(2), Set.of("b")),
				List.of(feedback(2, FeedbackValue.LIKE, DAY_AFTER)), List.of(), List.of(), Map.of());

		// 히스토리: 반응 없음 → x = 0 ÷ 3.5 = 0
		assertThat(InterestWindow.levels(record, T, T).levels()).containsEntry("b", 0.0);
		// 다이제스트: 좋아요 → x = 0.5 ÷ 3.5 = 1/7
		assertThat(InterestWindow.levels(record, T, NOW).level("b")).isCloseTo(0.14285714285714285, EXACT);
	}

	@Test
	void rowsBeforeTheWindowStartAreLeftOut() {
		Instant beforeStart = InterestWindow.start(T).minusSeconds(1);
		// 사건 3(c): 창 안 30일 전 노출, 좋아요는 창 시작 앞 · 사건 4(d): 창 시작 앞 노출 · 좋아요
		InterestRecord record = record(List.of(exposure(4, beforeStart), exposure(3, MONTH_BEFORE)),
				Map.of(event(3), Set.of("c"), event(4), Set.of("d")),
				List.of(feedback(3, FeedbackValue.LIKE, beforeStart), feedback(4, FeedbackValue.LIKE, beforeStart)),
				List.of(), List.of(), Map.of());

		InterestLevels levels = InterestWindow.levels(record, T, T);

		assertThat(levels.levels()).containsOnlyKeys("c");
		assertThat(levels.level("c")).as("창 시작 앞의 좋아요는 읽지 않는다").isEqualTo(0.0);
	}

	@Test
	void theLastReactionInTheWindowCounts() {
		// 사건 5(e): 30일 전 노출, 좋아요 → 없음 → 싫어요(모두 창 안, 기준 시각 앞)
		InterestRecord record = record(List.of(exposure(5, MONTH_BEFORE)), Map.of(event(5), Set.of("e")),
				List.of(feedback(5, FeedbackValue.LIKE, T.minus(Duration.ofDays(3))),
						feedback(5, FeedbackValue.NONE, T.minus(Duration.ofDays(2))),
						feedback(5, FeedbackValue.DISLIKE, T.minus(Duration.ofDays(1)))),
				List.of(), List.of(), Map.of());

		// x = −0.5 ÷ 3.5 = −1/7
		assertThat(InterestWindow.levels(record, T, T).level("e")).isCloseTo(-0.14285714285714285, EXACT);
	}

	@Test
	void subscriptionChangesInTheWindowAreSignalsByTheSubscribersOwnHand() {
		StorylineId kept = new StorylineId(31);
		InterestRecord record = record(List.of(), Map.of(), List.of(),
				List.of(topic("f", TopicSubscriptionCause.USER, MONTH_BEFORE),
						topic("g", TopicSubscriptionCause.ONBOARDING, MONTH_BEFORE),
						topic("h", TopicSubscriptionCause.USER, DAY_AFTER)),
				List.of(storyline(kept, kept, MONTH_BEFORE), storyline(new StorylineId(32), null, MONTH_BEFORE)),
				Map.of(kept, Map.of("i", 0.5)));

		InterestLevels history = InterestWindow.levels(record, T, T);

		// 주제 구독 더함 30일 전 → S = 0.5 → x = 0.5 ÷ 3 = 1/6
		assertThat(history.level("f")).isCloseTo(0.16666666666666666, EXACT);
		// 스토리라인 구독(몫 0.5) 30일 전 → S = 0.25 → x = 0.25 ÷ 3 = 1/12
		assertThat(history.level("i")).isCloseTo(0.08333333333333333, EXACT);
		assertThat(history.levels()).as("기본값 · 기준 시각 뒤 · 지워진 스토리라인의 행은 신호가 아니다").containsOnlyKeys("f", "i");
		// 다이제스트 창에는 기준 시각 하루 뒤의 더함이 무게 1 로 든다 → x = 1 ÷ 3
		assertThat(InterestWindow.levels(record, T, NOW).level("h")).isCloseTo(0.3333333333333333, EXACT);
	}

	@Test
	void aWindowEndingBeforeTheReferenceReadsUpToItsEndAndAnEmptyWindowHasNoInterest() {
		// 사건 6(j): 30일 전 노출 · 좋아요, 사건 7(k): 10일 전 노출
		InterestRecord record = record(List.of(exposure(6, MONTH_BEFORE), exposure(7, T.minus(Duration.ofDays(10)))),
				Map.of(event(6), Set.of("j"), event(7), Set.of("k")),
				List.of(feedback(6, FeedbackValue.LIKE, MONTH_BEFORE)), List.of(), List.of(), Map.of());

		// 창 끝 = 기준 시각 20일 전 — 30일 전 노출 · 좋아요만 → x = 0.5 ÷ 3.5 = 1/7
		InterestLevels early = InterestWindow.levels(record, T, T.minus(Duration.ofDays(20)));
		assertThat(early.levels()).containsOnlyKeys("j");
		assertThat(early.level("j")).isCloseTo(0.14285714285714285, EXACT);
		// 창 끝이 창 시작 이하면 빈 창
		assertThat(InterestWindow.levels(record, T, InterestWindow.start(T))).isEqualTo(InterestLevels.NONE);
		assertThat(InterestWindow.levels(record, T, InterestWindow.start(T).minusSeconds(1)))
			.isEqualTo(InterestLevels.NONE);
	}

	@Test
	void theWindowIncludesItsStartAndExcludesItsEnd() {
		Instant start = InterestWindow.start(T);
		// 사건 8(l): 창 시작 정각 노출 · 좋아요, 사건 9(m): 기준 시각 정각 노출
		InterestRecord record = record(List.of(exposure(8, start), exposure(9, T)),
				Map.of(event(8), Set.of("l"), event(9), Set.of("m")),
				List.of(feedback(8, FeedbackValue.LIKE, start)), List.of(), List.of(), Map.of());

		InterestLevels history = InterestWindow.levels(record, T, T);

		assertThat(history.levels()).as("기준 시각 정각의 노출은 다음 기간의 것").containsOnlyKeys("l");
		// 120일 전 무게 1/16 → x = 0.0625 ÷ 3.0625 = 1/49
		assertThat(history.level("l")).isCloseTo(0.02040816326530612, EXACT);
	}

	@Test
	void theLatestExposureGivesTheShareOfAnEventShownTwice() {
		// 사건 1(a): 30일 전 항목 셋의 하나(몫 1/3), 하루 뒤 혼자인 항목(몫 1) · 좋아요 — 다이제스트 창에서 가장 늦은 몫 1, 무게 1 → x = 1 ÷ 4
		InterestRecord record = record(List.of(exposure(1, event(1), MONTH_BEFORE, 1.0 / 3), exposure(2, event(1), DAY_AFTER, 1.0)),
				Map.of(event(1), Set.of("a")), List.of(feedback(1, FeedbackValue.LIKE, T)), List.of(), List.of(), Map.of());

		assertThat(InterestWindow.levels(record, T, NOW).level("a")).isCloseTo(0.25, EXACT);
	}

	@Test
	void theLatestExposureGivesItsShareEvenWhenItIsTheSmallerOne() {
		// 대조 — 몫이 뒤집히면(30일 전 1, 하루 뒤 1/3) 가장 늦은 몫 1/3 → E = F = 1/3 → x = (1/3) ÷ (10/3) = 1/10
		InterestRecord record = record(List.of(exposure(1, event(1), MONTH_BEFORE, 1.0), exposure(2, event(1), DAY_AFTER, 1.0 / 3)),
				Map.of(event(1), Set.of("a")), List.of(feedback(1, FeedbackValue.LIKE, T)), List.of(), List.of(), Map.of());

		assertThat(InterestWindow.levels(record, T, NOW).level("a")).isCloseTo(0.1, EXACT);
	}

	@Test
	void eventsOfOneItemMergedIntoOneGatherTheirShares() {
		// 항목 {1, 2, 3} 의 1 · 2 가 2 로 합쳐졌다 — 지금 사건으로 푼 행은 (2, 1/3) 둘 · (3, 1/3). 2 에 좋아요 → 2 의 몫 2/3
		// → E = F = 2/3 → x = (2/3) ÷ (11/3) = 2/11. 3 은 몫 1/3 · 반응 없음 → x = 0
		InterestRecord record = record(
				List.of(exposure(5, event(2), DAY_AFTER, 1.0 / 3), exposure(5, event(2), DAY_AFTER, 1.0 / 3),
						exposure(5, event(3), DAY_AFTER, 1.0 / 3)),
				Map.of(event(2), Set.of("b"), event(3), Set.of("c")),
				List.of(new FeedbackChange(event(1), event(2), FeedbackValue.LIKE, 5, T)), List.of(), List.of(), Map.of());

		InterestLevels levels = InterestWindow.levels(record, T, NOW);

		assertThat(levels.level("b")).isCloseTo(0.18181818181818182, EXACT);
		assertThat(levels.levels()).containsEntry("c", 0.0);
	}

	@Test
	void sharesGatheredInOneDigestAreCutAtOne() {
		// 서로 다른 항목 둘(몫 1 · 1/2)이 한 다이제스트에서 같은 사건으로 풀렸다 — 1.5 를 1 로 자른다 → 좋아요 x = 1 ÷ 4
		InterestRecord record = record(List.of(exposure(5, event(2), DAY_AFTER, 1.0), exposure(5, event(2), DAY_AFTER, 0.5)),
				Map.of(event(2), Set.of("b")), List.of(feedback(2, FeedbackValue.LIKE, T)), List.of(), List.of(), Map.of());

		assertThat(InterestWindow.levels(record, T, NOW).level("b")).isCloseTo(0.25, EXACT);
	}

	@Test
	void twoDigestsFinishedAtTheSameTimeDoNotMixTheirShares() {
		// 같은 시각에 끝낸 다이제스트 5(몫 1/2) · 6(몫 1/3) — 다이제스트 ID 가 큰 6 의 몫 1/3 → x = (1/3) ÷ (10/3) = 1/10
		InterestRecord record = record(List.of(exposure(6, event(2), DAY_AFTER, 1.0 / 3), exposure(5, event(2), DAY_AFTER, 0.5)),
				Map.of(event(2), Set.of("b")), List.of(feedback(2, FeedbackValue.LIKE, T)), List.of(), List.of(), Map.of());

		assertThat(InterestWindow.levels(record, T, NOW).level("b")).isCloseTo(0.1, EXACT);
	}

	private static InterestRecord record(List<ExposureRow> exposures, Map<EventId, Set<String>> subtopics,
			List<FeedbackChange> feedback, List<TopicSubscriptionChange> topics,
			List<StorylineSubscriptionChange> storylines, Map<StorylineId, Map<String, Double>> shares) {
		return new InterestRecord(TAG_SET, exposures, subtopics, feedback, topics, storylines, shares);
	}

	private static EventId event(long id) {
		return new EventId(id);
	}

	/** 몫 1 인 노출 행 — 다이제스트는 노출 시각마다 다르다 */
	private static ExposureRow exposure(long event, Instant at) {
		return exposure(at.getEpochSecond(), event(event), at, 1.0);
	}

	private static ExposureRow exposure(long digest, EventId event, Instant at, double share) {
		return new ExposureRow(digest, event, at, share);
	}

	/** 그대로인 사건의 이력 행 — 기록한 사건과 지금 사건이 같다 */
	private static FeedbackChange feedback(long event, FeedbackValue value, Instant at) {
		return new FeedbackChange(event(event), event(event), value, 1, at);
	}

	private static TopicSubscriptionChange topic(String subtopic, TopicSubscriptionCause cause, Instant at) {
		return new TopicSubscriptionChange(subtopic, TAG_SET, Set.of(subtopic), TopicSubscriptionAction.ADD, cause, at);
	}

	private static StorylineSubscriptionChange storyline(StorylineId recorded, StorylineId current, Instant at) {
		return new StorylineSubscriptionChange(recorded, current, StorylineSubscriptionAction.ADD,
				StorylineSubscriptionCause.USER, at);
	}

}
