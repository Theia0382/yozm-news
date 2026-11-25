package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
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
 * 기준 시각 하나의 관심 — 재료를 창으로 자른다. 기대값은 손으로 계산한 리터럴이다(반감기 30일 · K 3 · γ 주제 0.1 · 스토리라인 1/3, 30일 전
 * 무게 0.5). 다이제스트의 창 끝은 부르는 시각, 관심사 히스토리의 창 끝은 기준 시각이다. 노출 행은 지금 사건으로 푼 것이고, 몫은 따로
 * 적지 않으면 1 · 다이제스트는 행마다 다르다.
 * <p>
 * 스토리라인 · 반복 시험은 기준 시각 뒤에 노출해 무게를 1 로 두고(창 끝은 넉넉히 뒤), 다이제스트 날짜는 노출 시각의 UTC 날짜다.
 * <p>
 * 지키는 규칙: digest-R25 · digest-R35
 */
class InterestWindowTests {

	private static final Instant T = Instant.parse("2026-11-17T00:00:00Z");

	private static final Instant MONTH_BEFORE = T.minus(Duration.ofDays(30));

	private static final Instant DAY_AFTER = T.plus(Duration.ofDays(1));

	/** 다이제스트가 부르는 시각 — 기준 시각(기간 끝) 이틀 뒤 */
	private static final Instant NOW = T.plus(Duration.ofDays(2));

	/** 기준 시각 뒤의 노출을 무게 1 로 넉넉히 담는 창 끝 — 스토리라인 · 반복 시험 */
	private static final Instant LATER = T.plus(Duration.ofDays(30));

	private static final TagSetId TAG_SET = new TagSetId(7);

	private static final Offset<Double> EXACT = within(1e-12);

	/** 포함 나무의 꼭대기 */
	private static final StorylineId TREE = new StorylineId(100);

	private static final StorylineId OTHER_TREE = new StorylineId(200);

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
		assertThat(InterestWindow.levels(record, T, T).topics().level("a")).isCloseTo(0.14285714285714285, EXACT);
		// 다이제스트: 노출 하루 뒤(무게 1) · 좋아요 → x = 1 ÷ 4
		assertThat(InterestWindow.levels(record, T, NOW).topics().level("a")).isCloseTo(0.25, EXACT);
	}

	@Test
	void aReactionAfterTheReferenceIsLeftOutOfTheHistoryWindowButCountsForTheDigest() {
		// 사건 2(b): 30일 전 노출, 좋아요는 기준 시각 하루 뒤
		InterestRecord record = record(List.of(exposure(2, MONTH_BEFORE)), Map.of(event(2), Set.of("b")),
				List.of(feedback(2, FeedbackValue.LIKE, DAY_AFTER)), List.of(), List.of(), Map.of());

		// 히스토리: 반응 없음 → x = 0 ÷ 3.5 = 0
		assertThat(InterestWindow.levels(record, T, T).topics().levels()).containsEntry("b", 0.0);
		// 다이제스트: 좋아요 → x = 0.5 ÷ 3.5 = 1/7
		assertThat(InterestWindow.levels(record, T, NOW).topics().level("b")).isCloseTo(0.14285714285714285, EXACT);
	}

	@Test
	void rowsBeforeTheWindowStartAreLeftOut() {
		Instant beforeStart = InterestWindow.start(T).minusSeconds(1);
		// 사건 3(c): 창 안 30일 전 노출, 좋아요는 창 시작 앞 · 사건 4(d): 창 시작 앞 노출 · 좋아요
		InterestRecord record = record(List.of(exposure(4, beforeStart), exposure(3, MONTH_BEFORE)),
				Map.of(event(3), Set.of("c"), event(4), Set.of("d")),
				List.of(feedback(3, FeedbackValue.LIKE, beforeStart), feedback(4, FeedbackValue.LIKE, beforeStart)),
				List.of(), List.of(), Map.of());

		InterestLevels levels = InterestWindow.levels(record, T, T).topics();

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
		assertThat(InterestWindow.levels(record, T, T).topics().level("e")).isCloseTo(-0.14285714285714285, EXACT);
	}

	@Test
	void subscriptionChangesInTheWindowAreSignalsByTheSubscribersOwnHand() {
		StorylineId kept = new StorylineId(31);
		InterestRecord record = record(List.of(), Map.of(), List.of(),
				List.of(topic("f", TopicSubscriptionCause.USER, MONTH_BEFORE),
						topic("g", TopicSubscriptionCause.ONBOARDING, MONTH_BEFORE),
						topic("h", TopicSubscriptionCause.USER, DAY_AFTER)),
				List.of(storyline(kept, kept, MONTH_BEFORE), storyline(new StorylineId(32), null, MONTH_BEFORE)),
				Map.of(kept, TREE));

		Interests history = InterestWindow.levels(record, T, T);

		// 주제 구독 더함 30일 전 → S = 0.5 → x = 0.1 × 0.5 = 0.05
		assertThat(history.topics().level("f")).isCloseTo(0.05, EXACT);
		assertThat(history.topics().levels()).as("기본값 · 기준 시각 뒤의 행은 신호가 아니고, 스토리라인 구독은 주제에 들지 않는다")
			.containsOnlyKeys("f");
		// 스토리라인 구독(꼭대기 TREE) 30일 전 → S = 0.5 → x = (1/3) × 0.5 = 1/6. 지워진 스토리라인의 행은 신호가 아니다
		assertThat(history.storylines().levels()).containsOnlyKeys(TREE);
		assertThat(history.storylines().level(TREE)).isCloseTo(0.16666666666666666, EXACT);
		// 다이제스트 창에는 기준 시각 하루 뒤의 더함이 무게 1 로 든다 → x = 0.1
		assertThat(InterestWindow.levels(record, T, NOW).topics().level("h")).isCloseTo(0.1, EXACT);
	}

	@Test
	void aWindowEndingBeforeTheReferenceReadsUpToItsEndAndAnEmptyWindowHasNoInterest() {
		// 사건 6(j): 30일 전 노출 · 좋아요, 사건 7(k): 10일 전 노출
		InterestRecord record = record(List.of(exposure(6, MONTH_BEFORE), exposure(7, T.minus(Duration.ofDays(10)))),
				Map.of(event(6), Set.of("j"), event(7), Set.of("k")),
				List.of(feedback(6, FeedbackValue.LIKE, MONTH_BEFORE)), List.of(), List.of(), Map.of());

		// 창 끝 = 기준 시각 20일 전 — 30일 전 노출 · 좋아요만 → x = 0.5 ÷ 3.5 = 1/7
		InterestLevels early = InterestWindow.levels(record, T, T.minus(Duration.ofDays(20))).topics();
		assertThat(early.levels()).containsOnlyKeys("j");
		assertThat(early.level("j")).isCloseTo(0.14285714285714285, EXACT);
		// 창 끝이 창 시작 이하면 빈 창
		assertThat(InterestWindow.levels(record, T, InterestWindow.start(T))).isEqualTo(Interests.NONE);
		assertThat(InterestWindow.levels(record, T, InterestWindow.start(T).minusSeconds(1)))
			.isEqualTo(Interests.NONE);
	}

	@Test
	void theWindowIncludesItsStartAndExcludesItsEnd() {
		Instant start = InterestWindow.start(T);
		// 사건 8(l): 창 시작 정각 노출 · 좋아요, 사건 9(m): 기준 시각 정각 노출
		InterestRecord record = record(List.of(exposure(8, start), exposure(9, T)),
				Map.of(event(8), Set.of("l"), event(9), Set.of("m")),
				List.of(feedback(8, FeedbackValue.LIKE, start)), List.of(), List.of(), Map.of());

		InterestLevels history = InterestWindow.levels(record, T, T).topics();

		assertThat(history.levels()).as("기준 시각 정각의 노출은 다음 기간의 것").containsOnlyKeys("l");
		// 120일 전 무게 1/16 → x = 0.0625 ÷ 3.0625 = 1/49
		assertThat(history.level("l")).isCloseTo(0.02040816326530612, EXACT);
	}

	@Test
	void theLatestExposureGivesTheShareOfAnEventShownTwice() {
		// 사건 1(a): 30일 전 항목 셋의 하나(몫 1/3), 하루 뒤 혼자인 항목(몫 1) · 좋아요 — 다이제스트 창에서 가장 늦은 몫 1, 무게 1 → x = 1 ÷ 4
		InterestRecord record = record(List.of(exposure(1, event(1), MONTH_BEFORE, 1.0 / 3), exposure(2, event(1), DAY_AFTER, 1.0)),
				Map.of(event(1), Set.of("a")), List.of(feedback(1, FeedbackValue.LIKE, T)), List.of(), List.of(), Map.of());

		assertThat(InterestWindow.levels(record, T, NOW).topics().level("a")).isCloseTo(0.25, EXACT);
	}

	@Test
	void theLatestExposureGivesItsShareEvenWhenItIsTheSmallerOne() {
		// 대조 — 몫이 뒤집히면(30일 전 1, 하루 뒤 1/3) 가장 늦은 몫 1/3 → E = F = 1/3 → x = (1/3) ÷ (10/3) = 1/10
		InterestRecord record = record(List.of(exposure(1, event(1), MONTH_BEFORE, 1.0), exposure(2, event(1), DAY_AFTER, 1.0 / 3)),
				Map.of(event(1), Set.of("a")), List.of(feedback(1, FeedbackValue.LIKE, T)), List.of(), List.of(), Map.of());

		assertThat(InterestWindow.levels(record, T, NOW).topics().level("a")).isCloseTo(0.1, EXACT);
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

		InterestLevels levels = InterestWindow.levels(record, T, NOW).topics();

		assertThat(levels.level("b")).isCloseTo(0.18181818181818182, EXACT);
		assertThat(levels.levels()).containsEntry("c", 0.0);
	}

	@Test
	void sharesGatheredInOneDigestAreCutAtOne() {
		// 서로 다른 항목 둘(몫 1 · 1/2)이 한 다이제스트에서 같은 사건으로 풀렸다 — 1.5 를 1 로 자른다 → 좋아요 x = 1 ÷ 4
		InterestRecord record = record(List.of(exposure(5, event(2), DAY_AFTER, 1.0), exposure(5, event(2), DAY_AFTER, 0.5)),
				Map.of(event(2), Set.of("b")), List.of(feedback(2, FeedbackValue.LIKE, T)), List.of(), List.of(), Map.of());

		assertThat(InterestWindow.levels(record, T, NOW).topics().level("b")).isCloseTo(0.25, EXACT);
	}

	@Test
	void twoDigestsFinishedAtTheSameTimeDoNotMixTheirShares() {
		// 같은 시각에 끝낸 다이제스트 5(몫 1/2) · 6(몫 1/3) — 다이제스트 ID 가 큰 6 의 몫 1/3 → x = (1/3) ÷ (10/3) = 1/10
		InterestRecord record = record(List.of(exposure(6, event(2), DAY_AFTER, 1.0 / 3), exposure(5, event(2), DAY_AFTER, 0.5)),
				Map.of(event(2), Set.of("b")), List.of(feedback(2, FeedbackValue.LIKE, T)), List.of(), List.of(), Map.of());

		assertThat(InterestWindow.levels(record, T, NOW).topics().level("b")).isCloseTo(0.1, EXACT);
	}

	@Test
	void aDislikeOnAnEventOfATreeLowersTheInterestOfItsTop() {
		// 대조 — 꼭대기 없는 사건 2 는 스토리라인 관심을 만들지 않는다
		InterestRecord untreed = record(List.of(exposure(2, DAY_AFTER)), Map.of(event(2), Set.of("t")), Map.of(),
				List.of(feedback(2, FeedbackValue.DISLIKE, DAY_AFTER)));
		assertThat(InterestWindow.levels(untreed, T, LATER).storylines()).isEqualTo(StorylineLevels.NONE);
		// 꼭대기 TREE 인 사건 1(몫 1, 기준 시각 뒤라 무게 1)에 싫어요 → TREE = −1 ÷ (1 + 3) = −0.25
		InterestRecord record = record(List.of(exposure(1, DAY_AFTER)), Map.of(event(1), Set.of("t")),
				Map.of(event(1), List.of(TREE)), List.of(feedback(1, FeedbackValue.DISLIKE, DAY_AFTER)));

		assertThat(InterestWindow.levels(record, T, LATER).storylines().level(TREE)).isCloseTo(-0.25, EXACT);
	}

	@Test
	void anEventOnTwoTreesSplitsItsShareBetweenTheirTops() {
		// 대조 — 꼭대기 하나면 1 ÷ (1 + 3) = 0.25
		InterestRecord single = record(List.of(exposure(1, DAY_AFTER)), Map.of(event(1), Set.of("t")),
				Map.of(event(1), List.of(TREE)), List.of(feedback(1, FeedbackValue.LIKE, DAY_AFTER)));
		assertThat(InterestWindow.levels(single, T, LATER).storylines().level(TREE)).isCloseTo(0.25, EXACT);
		// 꼭대기 둘 — 꼭대기마다 몫 배수 1/2 → E = F = 0.5 → x = 0.5 ÷ 3.5 = 1/7
		InterestRecord record = record(List.of(exposure(1, DAY_AFTER)), Map.of(event(1), Set.of("t")),
				Map.of(event(1), List.of(TREE, OTHER_TREE)), List.of(feedback(1, FeedbackValue.LIKE, DAY_AFTER)));

		StorylineLevels levels = InterestWindow.levels(record, T, LATER).storylines();

		assertThat(levels.level(TREE)).isCloseTo(0.14285714285714285, EXACT);
		assertThat(levels.level(OTHER_TREE)).isCloseTo(0.14285714285714285, EXACT);
	}

	@Test
	void anEventWithATopButNoCountableTopicStillCountsForItsTop() {
		// 대조 — 같은 사건에 주제가 있으면 처음 본 스토리라인이라 주제에도 든다: t = −1 ÷ 4
		InterestRecord topical = record(List.of(exposure(1, DAY_AFTER)), Map.of(event(1), Set.of("t")),
				Map.of(event(1), List.of(TREE)), List.of(feedback(1, FeedbackValue.DISLIKE, DAY_AFTER)));
		assertThat(InterestWindow.levels(topical, T, LATER).topics().level("t")).isCloseTo(-0.25, EXACT);
		// 평가 있는 소속 기사가 없는 사건(주제 없음) — 주제에는 들지 않고 꼭대기에는 든다
		InterestRecord record = record(List.of(exposure(1, DAY_AFTER)), Map.of(), Map.of(event(1), List.of(TREE)),
				List.of(feedback(1, FeedbackValue.DISLIKE, DAY_AFTER)));

		Interests interests = InterestWindow.levels(record, T, LATER);

		assertThat(interests.topics()).isEqualTo(InterestLevels.NONE);
		assertThat(interests.storylines().level(TREE)).isCloseTo(-0.25, EXACT);
	}

	@Test
	void aDislikeOnAStorylineSeenTheDayBeforeStaysOutOfTheTopic() {
		// 대조 — b 가 a 의 8일 뒤(7일 밖)면 처음 본 스토리라인: t = −1 ÷ (2 + 3) = −0.2. TREE 는 스토리라인 묶음이 반응이 앞선 무반응 노출을
		// 대신해 a 가 빠진다 — −1 ÷ (1 + 3) = −0.25. 주제는 대신하지 않아 a 가 남는다
		InterestRecord late = sameTree(T.plus(Duration.ofDays(9)));
		assertThat(InterestWindow.levels(late, T, LATER).topics().level("t")).isCloseTo(-0.2, EXACT);
		assertThat(InterestWindow.levels(late, T, LATER).storylines().level(TREE)).isCloseTo(-0.25, EXACT);
		// 같은 꼭대기 TREE 의 사건 a(하루 뒤, 주제 t)와 b(이틀 뒤, 주제 t), b 에 싫어요 — b 는 반복이라 주제에 없다
		InterestRecord record = sameTree(T.plus(Duration.ofDays(2)));

		Interests interests = InterestWindow.levels(record, T, LATER);

		// 주제 t: a 만 — E = 1 · F = 0 → x = 0 ÷ 4 = 0. TREE: b 의 싫어요가 앞선 무반응 a 를 대신한다 — E = 1 · F = −1 → x = −1 ÷ 4 = −0.25
		assertThat(interests.topics().levels()).containsEntry("t", 0.0);
		assertThat(interests.storylines().level(TREE)).isCloseTo(-0.25, EXACT);
	}

	@Test
	void aRepeatIsJudgedOnlyByRowsInsideTheWindow() {
		Instant start = InterestWindow.start(T);
		// 대조 — 창 안에 같은 꼭대기의 앞 노출(사건 3, 창 시작 정각)이 있으면 반복이라 주제에 없다
		InterestRecord seen = record(
				List.of(exposure(3, start), exposure(2, start.plus(Duration.ofDays(1)))),
				Map.of(event(3), Set.of("s"), event(2), Set.of("t")),
				Map.of(event(3), List.of(TREE), event(2), List.of(TREE)), List.of());
		assertThat(InterestWindow.levels(seen, T, T).topics().levels()).containsOnlyKeys("s");
		// 같은 꼭대기의 사건 1 이 창 시작 하루 앞(창 밖)에, 사건 2 가 창 시작 하루 뒤에 노출 — 날짜로는 이틀 사이지만 창 밖 행은 보지 않는다
		InterestRecord record = record(
				List.of(exposure(1, start.minus(Duration.ofDays(1))), exposure(2, start.plus(Duration.ofDays(1)))),
				Map.of(event(1), Set.of("s"), event(2), Set.of("t")),
				Map.of(event(1), List.of(TREE), event(2), List.of(TREE)), List.of());

		assertThat(InterestWindow.levels(record, T, T).topics().levels()).as("처음 본 스토리라인이라 주제에 든다")
			.containsOnlyKeys("t");
	}

	@Test
	void aStorylineSubscriptionCountsOnlyForTheTopOfItsTree() {
		StorylineId lower = new StorylineId(31);
		// 대조 — 원인이 합쳐짐인 행(합쳐진 33 의 구독을 lower 로 고쳐 쓴 것)은 신호가 아니다
		InterestRecord rewritten = new InterestRecord(TAG_SET, List.of(), Map.of(), Map.of(), List.of(), List.of(),
				List.of(new StorylineSubscriptionChange(new StorylineId(33), lower, StorylineSubscriptionAction.ADD,
						StorylineSubscriptionCause.STORYLINE_MERGED, DAY_AFTER)),
				Map.of(lower, TREE), userStorylinesOf(List.of(TREE)));
		assertThat(InterestWindow.levels(rewritten, T, LATER)).isEqualTo(Interests.NONE);
		// 하위 스토리라인 lower(꼭대기 TREE)를 사용자가 구독 — 기준 시각 뒤라 무게 1 → TREE = (1/3) × 1 = 1/3, 주제에는 들지 않는다
		InterestRecord record = new InterestRecord(TAG_SET, List.of(), Map.of(), Map.of(), List.of(), List.of(),
				List.of(storyline(lower, lower, DAY_AFTER)), Map.of(lower, TREE), userStorylinesOf(List.of(TREE)));

		Interests interests = InterestWindow.levels(record, T, LATER);

		assertThat(interests.topics()).isEqualTo(InterestLevels.NONE);
		assertThat(interests.storylines().levels()).containsOnlyKeys(TREE);
		assertThat(interests.storylines().level(TREE)).isCloseTo(0.3333333333333333, EXACT);
	}

	@Test
	void theTopsOfOneUserStorylineShareOneInterest() {
		// 앞 스토리라인 TREE 의 사건 셋을 하루 뒤에 보고 모두 싫어요 — 뒤 스토리라인 OTHER_TREE 는 본 적이 없다
		List<ExposureRow> exposures = List.of(exposure(1, DAY_AFTER), exposure(2, DAY_AFTER), exposure(3, DAY_AFTER));
		Map<EventId, List<StorylineId>> tops = Map.of(event(1), List.of(TREE), event(2), List.of(TREE), event(3),
				List.of(TREE));
		List<FeedbackChange> dislikes = List.of(feedback(1, FeedbackValue.DISLIKE, DAY_AFTER),
				feedback(2, FeedbackValue.DISLIKE, DAY_AFTER), feedback(3, FeedbackValue.DISLIKE, DAY_AFTER));
		// 대조 — 두 꼭대기가 다른 묶음이면 OTHER_TREE 에는 값이 없다(중립)
		StorylineLevels apart = InterestWindow
			.levels(new InterestRecord(TAG_SET, exposures, Map.of(), tops, dislikes, List.of(), List.of(), Map.of(),
					new UserStorylines(Map.of(TREE, Set.of(0), OTHER_TREE, Set.of(1)), Set.of())), T, LATER)
			.storylines();
		assertThat(apart.levels()).containsOnlyKeys(TREE);
		assertThat(apart.level(TREE)).isCloseTo(-0.5, EXACT);

		// 두 꼭대기가 한 묶음 — E = 3 · F = −3 → x = −3 ÷ (3 + 3) = −0.5 가 두 꼭대기에 같은 값으로 든다
		StorylineLevels together = InterestWindow
			.levels(new InterestRecord(TAG_SET, exposures, Map.of(), tops, dislikes, List.of(), List.of(), Map.of(),
					new UserStorylines(Map.of(TREE, Set.of(0), OTHER_TREE, Set.of(0)), Set.of())), T, LATER)
			.storylines();

		assertThat(together.levels()).containsOnlyKeys(TREE, OTHER_TREE);
		assertThat(together.level(TREE)).isCloseTo(-0.5, EXACT);
		assertThat(together.level(OTHER_TREE)).isCloseTo(-0.5, EXACT);
	}

	@Test
	void aTopInSeveralUserStorylinesGetsTheValuesOfAllOfThem() {
		// 갈라지기 전 꼭대기 TREE 는 묶음 0 · 1 에, 갈래 OTHER_TREE 는 묶음 1 에만. TREE 의 사건 하나에 좋아요 · OTHER_TREE 의 사건
		// 둘에 싫어요(모두 하루 뒤, 무게 1) — 묶음 0: E = 1 · F = 1 → 1 ÷ 4, 묶음 1: E = 3 · F = −1 → −1 ÷ 6
		List<ExposureRow> exposures = List.of(exposure(1, DAY_AFTER), exposure(2, DAY_AFTER), exposure(3, DAY_AFTER));
		Map<EventId, List<StorylineId>> tops = Map.of(event(1), List.of(TREE), event(2), List.of(OTHER_TREE), event(3),
				List.of(OTHER_TREE));
		List<FeedbackChange> reactions = List.of(feedback(1, FeedbackValue.LIKE, DAY_AFTER),
				feedback(2, FeedbackValue.DISLIKE, DAY_AFTER), feedback(3, FeedbackValue.DISLIKE, DAY_AFTER));

		StorylineLevels levels = InterestWindow
			.levels(new InterestRecord(TAG_SET, exposures, Map.of(), tops, reactions, List.of(), List.of(), Map.of(),
					new UserStorylines(Map.of(TREE, Set.of(0, 1), OTHER_TREE, Set.of(1)), Set.of())), T, LATER)
			.storylines();

		// TREE 의 관심은 두 묶음 값의 평균 (1/4 − 1/6) ÷ 2 = 1/24, 남길 스토리라인은 묶음 하나라도(1/4 ≥ 0.2)
		assertThat(levels.level(TREE)).isCloseTo(0.041666666666666664, EXACT);
		assertThat(levels.level(OTHER_TREE)).isCloseTo(-0.16666666666666666, EXACT);
		assertThat(levels.kept()).containsExactly(TREE);
		assertThat(levels.excluded()).isEmpty();
	}

	@Test
	void aTopWithoutAUserStorylineHasNoStorylineInterestButKeepsItsTopic() {
		List<ExposureRow> exposures = List.of(exposure(1, DAY_AFTER));
		Map<EventId, List<StorylineId>> tops = Map.of(event(1), List.of(TREE));
		List<FeedbackChange> dislike = List.of(feedback(1, FeedbackValue.DISLIKE, DAY_AFTER));
		// 대조 — 묶음이 있으면 TREE = −1 ÷ 4
		assertThat(InterestWindow
			.levels(new InterestRecord(TAG_SET, exposures, Map.of(event(1), Set.of("t")), tops, dislike, List.of(),
					List.of(), Map.of(), new UserStorylines(Map.of(TREE, Set.of(0)), Set.of())), T, LATER)
			.storylines()
			.level(TREE)).isCloseTo(-0.25, EXACT);

		Interests interests = InterestWindow
			.levels(new InterestRecord(TAG_SET, exposures, Map.of(event(1), Set.of("t")), tops, dislike, List.of(),
					List.of(), Map.of(), UserStorylines.NONE), T, LATER);

		assertThat(interests.storylines().levels()).isEmpty();
		assertThat(interests.topics().level("t")).as("주제 쪽 값은 그대로다").isCloseTo(-0.25, EXACT);
	}

	@Test
	void trackingSignalsOfOneUserStorylineAddUpAndAnInheritedSeedIsNotASignal() {
		StorylineId first = new StorylineId(31);
		StorylineId second = new StorylineId(32);
		UserStorylines userStorylines = new UserStorylines(Map.of(TREE, Set.of(0), OTHER_TREE, Set.of(0)), Set.of(0));
		Map<StorylineId, StorylineId> storylineTops = Map.of(first, TREE, second, OTHER_TREE);
		// 대조 — 사용자가 누른 것 하나: (1/3) × 1 = 1/3
		assertThat(InterestWindow
			.levels(new InterestRecord(TAG_SET, List.of(), Map.of(), Map.of(), List.of(), List.of(),
					List.of(storyline(first, first, DAY_AFTER)), storylineTops, userStorylines), T, LATER)
			.storylines()
			.level(OTHER_TREE)).isCloseTo(0.3333333333333333, EXACT);
		// 대조 — 갈라질 때 물려받은 추적 씨앗의 행은 신호가 아니다
		assertThat(InterestWindow
			.levels(new InterestRecord(TAG_SET, List.of(), Map.of(), Map.of(), List.of(), List.of(),
					List.of(storyline(first, first, DAY_AFTER),
							new StorylineSubscriptionChange(second, second, StorylineSubscriptionAction.ADD,
									StorylineSubscriptionCause.STORYLINE_SPLIT, DAY_AFTER)),
					storylineTops, userStorylines), T, LATER)
			.storylines()
			.level(TREE)).isCloseTo(0.3333333333333333, EXACT);

		// 한 묶음의 두 스토리라인을 사용자가 눌렀다 — 그 묶음에 +2: (1/3) × 2 = 2/3
		StorylineLevels levels = InterestWindow
			.levels(new InterestRecord(TAG_SET, List.of(), Map.of(), Map.of(), List.of(), List.of(),
					List.of(storyline(first, first, DAY_AFTER), storyline(second, second, DAY_AFTER)), storylineTops,
					userStorylines), T, LATER)
			.storylines();

		assertThat(levels.level(TREE)).isCloseTo(0.6666666666666666, EXACT);
		assertThat(levels.level(OTHER_TREE)).isCloseTo(0.6666666666666666, EXACT);
	}

	/** 같은 꼭대기 TREE 의 사건 a(1 — 하루 뒤)와 b(2 — {@code second}), 둘 다 주제 t, b 에 싫어요 */
	private static InterestRecord sameTree(Instant second) {
		return record(List.of(exposure(1, DAY_AFTER), exposure(2, second)),
				Map.of(event(1), Set.of("t"), event(2), Set.of("t")),
				Map.of(event(1), List.of(TREE), event(2), List.of(TREE)),
				List.of(feedback(2, FeedbackValue.DISLIKE, second)));
	}

	/** 꼭대기 없는 재료 — 주제 관심만의 시험 */
	private static InterestRecord record(List<ExposureRow> exposures, Map<EventId, Set<String>> subtopics,
			List<FeedbackChange> feedback, List<TopicSubscriptionChange> topics,
			List<StorylineSubscriptionChange> storylines, Map<StorylineId, StorylineId> storylineTops) {
		return new InterestRecord(TAG_SET, exposures, subtopics, Map.of(), feedback, topics, storylines,
				storylineTops, userStorylinesOf(List.copyOf(storylineTops.values())));
	}

	/** 노출 사건의 꼭대기가 있는 재료 */
	private static InterestRecord record(List<ExposureRow> exposures, Map<EventId, Set<String>> subtopics,
			Map<EventId, List<StorylineId>> tops, List<FeedbackChange> feedback) {
		return new InterestRecord(TAG_SET, exposures, subtopics, tops, feedback, List.of(), List.of(), Map.of(),
				userStorylinesOf(tops.values().stream().flatMap(List::stream).toList()));
	}

	/** 꼭대기마다 제 사용자 스토리라인 묶음 하나 — 꼭대기 ID 순으로 번호를 준다 */
	private static UserStorylines userStorylinesOf(List<StorylineId> tops) {
		List<StorylineId> ordered = tops.stream().distinct().sorted(Comparator.comparingLong(StorylineId::value)).toList();
		Map<StorylineId, Set<Integer>> userStorylinesOfTop = new HashMap<>();
		for (int index = 0; index < ordered.size(); index++) {
			userStorylinesOfTop.put(ordered.get(index), Set.of(index));
		}
		return new UserStorylines(userStorylinesOfTop, Set.of());
	}

	private static EventId event(long id) {
		return new EventId(id);
	}

	/** 몫 1 인 노출 행 — 다이제스트는 노출 시각마다 다르다 */
	private static ExposureRow exposure(long event, Instant at) {
		return exposure(at.getEpochSecond(), event(event), at, 1.0);
	}

	private static ExposureRow exposure(long digest, EventId event, Instant at, double share) {
		return new ExposureRow(digest, LocalDate.ofInstant(at, ZoneOffset.UTC), event, at, share);
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
