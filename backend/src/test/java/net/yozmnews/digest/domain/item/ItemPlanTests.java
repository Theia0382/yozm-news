package net.yozmnews.digest.domain.item;

import static net.yozmnews.digest.domain.item.Items.big;
import static net.yozmnews.digest.domain.item.Items.subscribed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.digest.domain.interest.StorylineInterests;
import net.yozmnews.digest.domain.interest.TopicInterests;
import net.yozmnews.digest.domain.selection.SelectedEvent;
import net.yozmnews.event.EventId;
import org.junit.jupiter.api.Test;

/**
 * 항목 순위와 가르기 — 항목 점수 = 사건 점수의 최대(없으면 맨 뒤), 같으면 가장 작은 사건 ID. 요약문 길이가 기준 이상인 항목만 주요 뉴스가
 * 된다 — 큰 판정 나무의 항목은 모두, 나머지는 5 개까지. 그 밖은 헤드라인. 결과는 주요 뉴스 다음 헤드라인
 * <p>
 * 지키는 규칙: digest-R47
 */
class ItemPlanTests {

	/** 시험의 요약문 길이 기준 — 보통 단계의 주요 뉴스 요약 하한과 같은 값 */
	private static final int MIN = 200;

	/** 기준을 넉넉히 넘는 요약문 길이 — 요약문 길이 규칙이 걸리지 않는 시험에 쓴다 */
	private static final int ENOUGH = 500;

	@Test
	void bigItemsAreAllTopStoriesAndFiveMoreFollowThenHeadlines() {
		assertThat(ItemRules.TOP_STORIES).isEqualTo(5);
		// 큰 항목 둘(점수 3) + 나머지 일곱(점수 9 · 8 · 7 · 6 · 5 · 4 · 2)
		List<DigestItemPlan> items = new ArrayList<>();
		items.add(new DigestItemPlan(List.of(big(1, 0)), 0, null));
		items.add(new DigestItemPlan(List.of(big(2, 1)), 1, null));
		double[] scores = { 9, 8, 7, 6, 5, 4, 2 };
		for (int i = 0; i < scores.length; i++) {
			items.add(new DigestItemPlan(List.of(subscribed(10 + i, scores[i])), null, null));
		}

		List<ArrangedItem> arranged = arrangeWithEnough(items, InterestWeights.NEUTRAL_ALL);

		// 주요 뉴스: 9 · 8 · 7 · 6 · 5 다음 큰 항목 둘(3 — 사건 1 · 2 순)은 순위대로 섞이고, 4 · 2 는 헤드라인
		assertThat(arranged).extracting(item -> Items.ids(item.plan()).getFirst(), ArrangedItem::kind)
			.containsExactly(tuple(10L, ItemKind.TOP_STORY),
					tuple(11L, ItemKind.TOP_STORY),
					tuple(12L, ItemKind.TOP_STORY),
					tuple(13L, ItemKind.TOP_STORY),
					tuple(14L, ItemKind.TOP_STORY),
					tuple(1L, ItemKind.TOP_STORY),
					tuple(2L, ItemKind.TOP_STORY),
					tuple(15L, ItemKind.HEADLINE),
					tuple(16L, ItemKind.HEADLINE));
	}

	@Test
	void aHeadlineRankedAboveABigItemStillComesAfterAllTopStories() {
		// 큰 항목(점수 3)이 나머지 여섯(점수 9 · 8 · 7 · 6 · 5 · 4) 뒤 순위지만 주요 뉴스다 — 점수 4 의 여섯째는 헤드라인이고 큰 항목 뒤에 놓인다
		List<DigestItemPlan> items = new ArrayList<>();
		double[] scores = { 9, 8, 7, 6, 5, 4 };
		for (int i = 0; i < scores.length; i++) {
			items.add(new DigestItemPlan(List.of(subscribed(10 + i, scores[i])), null, null));
		}
		items.add(new DigestItemPlan(List.of(big(1, 0)), 0, null));

		List<ArrangedItem> arranged = arrangeWithEnough(items, InterestWeights.NEUTRAL_ALL);

		assertThat(arranged).extracting(item -> Items.ids(item.plan()).getFirst())
			.containsExactly(10L, 11L, 12L, 13L, 14L, 1L, 15L);
		assertThat(arranged.getLast().kind()).isEqualTo(ItemKind.HEADLINE);
		assertThat(arranged.get(5).kind()).isEqualTo(ItemKind.TOP_STORY);
	}

	@Test
	void theItemScoreIsTheBestEventScoreAndMissingScoresGoLast() {
		// 항목 A: 사건 5(2) · 사건 6(4.5) → 4.5. 항목 B: 사건 7(4) → 4. 항목 C: 사건 8(없음) → 맨 뒤
		DigestItemPlan a = new DigestItemPlan(List.of(subscribed(5, 2.0), subscribed(6, 4.5)), null, null);
		DigestItemPlan b = new DigestItemPlan(List.of(subscribed(7, 4.0)), null, null);
		DigestItemPlan c = new DigestItemPlan(List.of(subscribed(8, null)), null, null);

		List<ArrangedItem> arranged = arrangeWithEnough(List.of(c, b, a), InterestWeights.NEUTRAL_ALL);

		// 항목 안 사건은 사건 순위대로 — 6(4.5) 다음 5(2)
		assertThat(arranged).extracting(item -> Items.ids(item.plan()))
			.containsExactly(List.of(6L, 5L), List.of(7L), List.of(8L));
		assertThat(ItemPlan.score(a, InterestWeights.NEUTRAL_ALL).orElseThrow()).isEqualTo(4.5);
		assertThat(ItemPlan.score(c, InterestWeights.NEUTRAL_ALL)).isEmpty();
	}

	@Test
	void equalScoresAreOrderedByTheSmallestEventId() {
		DigestItemPlan later = new DigestItemPlan(List.of(subscribed(9)), null, null);
		DigestItemPlan earlier = new DigestItemPlan(List.of(subscribed(12), subscribed(4)), null, null);

		assertThat(arrangeWithEnough(List.of(later, earlier), InterestWeights.NEUTRAL_ALL))
			.extracting(item -> Items.ids(item.plan()).contains(4L))
			.containsExactly(true, false);
	}

	@Test
	void theItemScoreUsesTheInterestWeight() {
		// 정치 행정 가중치 0.5 — 항목 점수는 관심도를 곱한 사건 점수: 4 × 0.5 = 2.0, 3 × 0.5 = 1.5
		InterestWeights interests = new InterestWeights(new TopicInterests(Map.of("politics.executive", 0.5)), StorylineInterests.NEUTRAL_ALL);
		DigestItemPlan politics4 = new DigestItemPlan(List.of(subscribed(2, 4.0)), null, null);
		DigestItemPlan politics3 = new DigestItemPlan(List.of(subscribed(1, 3.0)), null, null);

		assertThat(ItemPlan.score(politics4, interests).orElseThrow()).isEqualTo(2.0);
		assertThat(arrangeWithEnough(List.of(politics3, politics4), interests))
			.extracting(item -> Items.ids(item.plan()).getFirst())
			.containsExactly(2L, 1L);
	}

	@Test
	void anItemNeedsDistinctEvents() {
		SelectedEvent event = subscribed(1);

		assertThatIllegalArgumentException()
			.isThrownBy(() -> new DigestItemPlan(List.of(), null, null));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new DigestItemPlan(List.of(event, event), null, null));
		assertThat(event.reason()).isEqualTo(SelectionReason.SUBSCRIBED);
	}

	@Test
	void anItemWithAShortSummaryLeavesItsTopStorySeatToTheNextItem() {
		// 큰 화제 없음, 항목 일곱(점수 9 ~ 3). 둘째(사건 11)만 요약문 길이 199 — 기준 200 에 못 미친다
		List<DigestItemPlan> items = subscribedItems(9, 8, 7, 6, 5, 4, 3);
		Map<EventId, Integer> lengths = Items.summaryLengths(ENOUGH, items);
		lengths.put(new EventId(11), 199);

		ItemArrangement arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, lengths, MIN);

		// 사건 11 의 자리를 일곱째 사건 16 이 아니라 여섯째 15 가 채운다 — 사건 11 은 헤드라인 맨 앞(순위 자리)
		assertThat(arranged.items()).extracting(item -> Items.ids(item.plan()).getFirst(), ArrangedItem::kind)
			.containsExactly(tuple(10L, ItemKind.TOP_STORY), tuple(12L, ItemKind.TOP_STORY),
					tuple(13L, ItemKind.TOP_STORY), tuple(14L, ItemKind.TOP_STORY), tuple(15L, ItemKind.TOP_STORY),
					tuple(11L, ItemKind.HEADLINE), tuple(16L, ItemKind.HEADLINE));
		assertThat(arranged.shortSummary()).isEqualTo(1);
	}

	@Test
	void anItemWhoseSummaryLengthEqualsTheMinimumIsATopStory() {
		// 위 시험의 대조 — 사건 11 의 요약문 길이가 기준과 같은 200 이면 주요 뉴스(경계 포함)
		List<DigestItemPlan> items = subscribedItems(9, 8, 7, 6, 5, 4, 3);
		Map<EventId, Integer> lengths = Items.summaryLengths(ENOUGH, items);
		lengths.put(new EventId(11), 200);

		ItemArrangement arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, lengths, MIN);

		assertThat(arranged.items()).extracting(item -> Items.ids(item.plan()).getFirst(), ArrangedItem::kind)
			.containsExactly(tuple(10L, ItemKind.TOP_STORY), tuple(11L, ItemKind.TOP_STORY),
					tuple(12L, ItemKind.TOP_STORY), tuple(13L, ItemKind.TOP_STORY), tuple(14L, ItemKind.TOP_STORY),
					tuple(15L, ItemKind.HEADLINE), tuple(16L, ItemKind.HEADLINE));
		assertThat(arranged.shortSummary()).isZero();
	}

	@Test
	void aBigItemWithAShortSummaryIsAHeadline() {
		// 큰 항목 둘(사건 1 · 2, 점수 3) + 나머지 여섯(점수 9 ~ 4). 큰 항목 사건 1 만 요약문 길이 150
		List<DigestItemPlan> items = new ArrayList<>(subscribedItems(9, 8, 7, 6, 5, 4));
		items.add(new DigestItemPlan(List.of(big(1, 0)), 0, null));
		items.add(new DigestItemPlan(List.of(big(2, 1)), 1, null));
		Map<EventId, Integer> lengths = Items.summaryLengths(ENOUGH, items);
		lengths.put(new EventId(1), 150);

		ItemArrangement arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, lengths, MIN);

		// 큰 화제 밖 다섯 다음 큰 항목 2 가 주요 뉴스, 큰 항목 1 은 헤드라인 — 큰 화제 자리는 다른 항목이 채우지 않는다
		assertThat(arranged.items()).extracting(item -> Items.ids(item.plan()).getFirst(), ArrangedItem::kind)
			.containsExactly(tuple(10L, ItemKind.TOP_STORY), tuple(11L, ItemKind.TOP_STORY),
					tuple(12L, ItemKind.TOP_STORY), tuple(13L, ItemKind.TOP_STORY), tuple(14L, ItemKind.TOP_STORY),
					tuple(2L, ItemKind.TOP_STORY), tuple(15L, ItemKind.HEADLINE), tuple(1L, ItemKind.HEADLINE));
		assertThat(arranged.shortSummary()).isEqualTo(1);
	}

	@Test
	void aBigItemWhoseSummaryLengthEqualsTheMinimumIsATopStory() {
		// 위 시험의 대조 — 큰 항목 사건 1 의 요약문 길이가 200 이면 두 큰 항목 모두 주요 뉴스
		List<DigestItemPlan> items = new ArrayList<>(subscribedItems(9, 8, 7, 6, 5, 4));
		items.add(new DigestItemPlan(List.of(big(1, 0)), 0, null));
		items.add(new DigestItemPlan(List.of(big(2, 1)), 1, null));
		Map<EventId, Integer> lengths = Items.summaryLengths(ENOUGH, items);
		lengths.put(new EventId(1), 200);

		ItemArrangement arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, lengths, MIN);

		assertThat(arranged.items()).extracting(item -> Items.ids(item.plan()).getFirst(), ArrangedItem::kind)
			.containsExactly(tuple(10L, ItemKind.TOP_STORY), tuple(11L, ItemKind.TOP_STORY),
					tuple(12L, ItemKind.TOP_STORY), tuple(13L, ItemKind.TOP_STORY), tuple(14L, ItemKind.TOP_STORY),
					tuple(1L, ItemKind.TOP_STORY), tuple(2L, ItemKind.TOP_STORY), tuple(15L, ItemKind.HEADLINE));
		assertThat(arranged.shortSummary()).isZero();
	}

	@Test
	void topStoriesShrinkWhenTooFewItemsHaveLongEnoughSummaries() {
		// 항목 여섯(점수 9 ~ 4, 사건 10 ~ 15) 가운데 요약문 길이가 기준 이상인 것은 사건 10 · 12 · 14 셋뿐
		List<DigestItemPlan> items = subscribedItems(9, 8, 7, 6, 5, 4);
		Map<EventId, Integer> lengths = Items.summaryLengths(100, items);
		lengths.put(new EventId(10), ENOUGH);
		lengths.put(new EventId(12), ENOUGH);
		lengths.put(new EventId(14), ENOUGH);

		ItemArrangement arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, lengths, MIN);

		// 채울 항목이 모자라 주요 뉴스는 셋 — 빠지는 항목은 없다
		assertThat(arranged.items()).extracting(item -> Items.ids(item.plan()).getFirst(), ArrangedItem::kind)
			.containsExactly(tuple(10L, ItemKind.TOP_STORY), tuple(12L, ItemKind.TOP_STORY),
					tuple(14L, ItemKind.TOP_STORY), tuple(11L, ItemKind.HEADLINE), tuple(13L, ItemKind.HEADLINE),
					tuple(15L, ItemKind.HEADLINE));
		assertThat(arranged.shortSummary()).isEqualTo(3);
	}

	@Test
	void onlyItemsMetWhileSeatsRemainAreCounted() {
		// 항목 여덟(점수 9 ~ 2, 사건 10 ~ 17). 셋째(12) · 일곱째(16) · 여덟째(17)가 요약문 길이 100
		List<DigestItemPlan> items = subscribedItems(9, 8, 7, 6, 5, 4, 3, 2);
		Map<EventId, Integer> lengths = Items.summaryLengths(ENOUGH, items);
		lengths.put(new EventId(12), 100);
		lengths.put(new EventId(16), 100);
		lengths.put(new EventId(17), 100);

		ItemArrangement arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, lengths, MIN);

		// 다섯 자리는 사건 15 에서 다 찬다 — 그 뒤의 16 · 17 은 규칙이 없었어도 헤드라인이라 세지 않는다
		assertThat(arranged.items()).extracting(item -> Items.ids(item.plan()).getFirst(), ArrangedItem::kind)
			.containsExactly(tuple(10L, ItemKind.TOP_STORY), tuple(11L, ItemKind.TOP_STORY),
					tuple(13L, ItemKind.TOP_STORY), tuple(14L, ItemKind.TOP_STORY), tuple(15L, ItemKind.TOP_STORY),
					tuple(12L, ItemKind.HEADLINE), tuple(16L, ItemKind.HEADLINE), tuple(17L, ItemKind.HEADLINE));
		assertThat(arranged.shortSummary()).isEqualTo(1);
	}

	@Test
	void theSummaryLengthOfAnItemIsTheSumOfItsEvents() {
		// 사건 둘(110 · 90)의 항목은 200 — 기준 이상. 사건 하나(199)의 항목은 기준 밑
		DigestItemPlan pair = new DigestItemPlan(List.of(subscribed(1, 5.0), subscribed(2, 4.0)), null, null);
		DigestItemPlan single = new DigestItemPlan(List.of(subscribed(3, 3.0)), null, null);

		ItemArrangement arranged = ItemPlan.arrange(List.of(pair, single), InterestWeights.NEUTRAL_ALL,
				Map.of(new EventId(1), 110, new EventId(2), 90, new EventId(3), 199), MIN);

		assertThat(arranged.items()).extracting(ArrangedItem::kind)
			.containsExactly(ItemKind.TOP_STORY, ItemKind.HEADLINE);
		assertThat(arranged.shortSummary()).isEqualTo(1);
	}

	@Test
	void anEventWithoutASummaryLengthOrANegativeMinimumIsRefused() {
		List<DigestItemPlan> items = subscribedItems(9, 8);
		Map<EventId, Integer> lengths = Items.summaryLengths(ENOUGH, items);
		lengths.remove(new EventId(11));

		assertThatIllegalArgumentException()
			.isThrownBy(() -> ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, lengths, MIN))
			.withMessageContaining("11");
		assertThatIllegalArgumentException().isThrownBy(() -> ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL,
				Items.summaryLengths(ENOUGH, items), -1));
	}

	@Test
	void anArrangementCountsAtMostItsHeadlines() {
		DigestItemPlan plan = new DigestItemPlan(List.of(subscribed(1)), null, null);
		List<ArrangedItem> oneHeadline = List.of(new ArrangedItem(plan, ItemKind.HEADLINE));
		List<ArrangedItem> oneTopStory = List.of(new ArrangedItem(plan, ItemKind.TOP_STORY));

		assertThat(new ItemArrangement(oneHeadline, 1).shortSummary()).isEqualTo(1);
		assertThatIllegalArgumentException().isThrownBy(() -> new ItemArrangement(oneHeadline, 2));
		assertThatIllegalArgumentException().isThrownBy(() -> new ItemArrangement(oneTopStory, 1));
		assertThatIllegalArgumentException().isThrownBy(() -> new ItemArrangement(oneHeadline, -1));
	}

	/** 요약문 길이 규칙이 걸리지 않게 모든 사건의 요약문 길이를 기준보다 넉넉히 두고 가른다 */
	private static List<ArrangedItem> arrangeWithEnough(List<DigestItemPlan> items, InterestWeights weights) {
		return ItemPlan.arrange(items, weights, Items.summaryLengths(ENOUGH, items), MIN).items();
	}

	/** 사건 하나씩의 구독 항목 — 사건 ID 는 10 부터 점수 순서대로 */
	private static List<DigestItemPlan> subscribedItems(double... scores) {
		List<DigestItemPlan> items = new ArrayList<>();
		for (int i = 0; i < scores.length; i++) {
			items.add(new DigestItemPlan(List.of(subscribed(10 + i, scores[i])), null, null));
		}
		return items;
	}

}
