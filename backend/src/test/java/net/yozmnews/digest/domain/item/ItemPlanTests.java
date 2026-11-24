package net.yozmnews.digest.domain.item;

import static net.yozmnews.digest.domain.item.Items.big;
import static net.yozmnews.digest.domain.item.Items.followed;
import static net.yozmnews.digest.domain.item.Items.row;
import static net.yozmnews.digest.domain.item.Items.subscribed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.digest.domain.interest.StorylineInterests;
import net.yozmnews.digest.domain.interest.TopicInterests;
import net.yozmnews.digest.domain.selection.SelectedEvent;
import net.yozmnews.digest.domain.selection.UserFilter;
import org.junit.jupiter.api.Test;

/**
 * 항목 순위와 가르기 — 항목 점수 = 사건 점수의 최대(없으면 맨 뒤), 같으면 가장 작은 사건 ID. 큰 판정 나무의 항목은 모두 주요 뉴스, 나머지는
 * 5 개까지 주요 뉴스, 그 뒤 헤드라인. 결과는 주요 뉴스 다음 헤드라인. 항목에 구독 행이 셋 이상 나오면 한 구독 행은 주요 뉴스 칸(그날 큰 항목 수
 * + 5)의 40% 까지이고, 큰 항목도 그 몫을 쓴다.
 * 손으로 적은 고른 사건({@link Items})과 구독 행으로 스프링 없이 돈다 — 상한을 보는 시험은 경제 · 정치 · 국제 세 대분류를 구독한 필터를 쓰고,
 * 사건 ID 1x 는 정치 행정 · 2x 는 국제 분쟁 · 3x 는 경제 시장이 주제다.
 * <p>
 * 지키는 규칙: digest-R47 · digest-R54
 */
class ItemPlanTests {

	/** 경제 · 정치 · 국제 세 대분류를 구독한 필터 — 키 순 */
	private static final UserFilter THREE_ROWS = Items.filter(row("economy", "economy.markets"),
			row("politics", Items.POLITICS_EXECUTIVE), row("world", "world.conflict"));

	@Test
	void bigItemsAreAllTopStoriesAndFiveMoreFollowThenHeadlines() {
		assertThat(ItemRules.TOP_STORIES).isEqualTo(5);
		// 큰 항목 둘(점수 3) + 나머지 일곱(점수 9 · 8 · 7 · 6 · 5 · 4 · 2)
		List<DigestItemPlan> items = new ArrayList<>();
		items.add(new DigestItemPlan(List.of(big(1, 0)), 0, null));
		items.add(new DigestItemPlan(List.of(big(2, 1)), 1, null));
		double[] scores = { 9, 8, 7, 6, 5, 4, 2 };
		for (int i = 0; i < scores.length; i++) {
			items.add(solo(subscribed(10 + i, scores[i])));
		}

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, Items.POLITICS);

		// 주요 뉴스: 9 · 8 · 7 · 6 · 5 다음 큰 항목 둘(3 — 사건 1 · 2 순)은 순위대로 섞이고, 4 · 2 는 헤드라인
		assertThat(arranged).extracting(item -> Items.ids(item.plan()).getFirst(), ArrangedItem::kind)
			.containsExactly(tuple(10L, ItemKind.TOP_STORY), tuple(11L, ItemKind.TOP_STORY),
					tuple(12L, ItemKind.TOP_STORY), tuple(13L, ItemKind.TOP_STORY), tuple(14L, ItemKind.TOP_STORY),
					tuple(1L, ItemKind.TOP_STORY), tuple(2L, ItemKind.TOP_STORY), tuple(15L, ItemKind.HEADLINE),
					tuple(16L, ItemKind.HEADLINE));
	}

	@Test
	void aHeadlineRankedAboveABigItemStillComesAfterAllTopStories() {
		// 큰 항목(점수 3)이 나머지 여섯(점수 9 · 8 · 7 · 6 · 5 · 4) 뒤 순위지만 주요 뉴스다 — 점수 4 의 여섯째는 헤드라인이고 큰 항목 뒤에 놓인다
		List<DigestItemPlan> items = new ArrayList<>();
		double[] scores = { 9, 8, 7, 6, 5, 4 };
		for (int i = 0; i < scores.length; i++) {
			items.add(solo(subscribed(10 + i, scores[i])));
		}
		items.add(new DigestItemPlan(List.of(big(1, 0)), 0, null));

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, Items.POLITICS);

		assertThat(arranged).extracting(item -> Items.ids(item.plan()).getFirst())
			.containsExactly(10L, 11L, 12L, 13L, 14L, 1L, 15L);
		assertThat(arranged.getLast().kind()).isEqualTo(ItemKind.HEADLINE);
		assertThat(arranged.get(5).kind()).isEqualTo(ItemKind.TOP_STORY);
	}

	@Test
	void theItemScoreIsTheBestEventScoreAndMissingScoresGoLast() {
		// 항목 A: 사건 5(2) · 사건 6(4.5) → 4.5. 항목 B: 사건 7(4) → 4. 항목 C: 사건 8(없음) → 맨 뒤
		DigestItemPlan a = new DigestItemPlan(List.of(subscribed(5, 2.0), subscribed(6, 4.5)), null, null);
		DigestItemPlan b = solo(subscribed(7, 4.0));
		DigestItemPlan c = solo(subscribed(8, null));

		List<ArrangedItem> arranged = ItemPlan.arrange(List.of(c, b, a), InterestWeights.NEUTRAL_ALL, Items.POLITICS);

		// 항목 안 사건은 사건 순위대로 — 6(4.5) 다음 5(2)
		assertThat(arranged).extracting(item -> Items.ids(item.plan()))
			.containsExactly(List.of(6L, 5L), List.of(7L), List.of(8L));
		assertThat(ItemPlan.score(a, InterestWeights.NEUTRAL_ALL).orElseThrow()).isEqualTo(4.5);
		assertThat(ItemPlan.score(c, InterestWeights.NEUTRAL_ALL)).isEmpty();
	}

	@Test
	void equalScoresAreOrderedByTheSmallestEventId() {
		DigestItemPlan later = solo(subscribed(9));
		DigestItemPlan earlier = new DigestItemPlan(List.of(subscribed(12), subscribed(4)), null, null);

		assertThat(ItemPlan.arrange(List.of(later, earlier), InterestWeights.NEUTRAL_ALL, Items.POLITICS))
			.extracting(item -> Items.ids(item.plan()).contains(4L))
			.containsExactly(true, false);
	}

	@Test
	void theItemScoreUsesTheInterestWeight() {
		// 정치 행정 가중치 0.5 — 항목 점수는 관심도를 곱한 사건 점수: 4 × 0.5 = 2.0, 3 × 0.5 = 1.5
		InterestWeights interests = new InterestWeights(new TopicInterests(Map.of("politics.executive", 0.5)), StorylineInterests.NEUTRAL_ALL);
		DigestItemPlan politics4 = solo(subscribed(2, 4.0));
		DigestItemPlan politics3 = solo(subscribed(1, 3.0));

		assertThat(ItemPlan.score(politics4, interests).orElseThrow()).isEqualTo(2.0);
		assertThat(ItemPlan.arrange(List.of(politics3, politics4), interests, Items.POLITICS))
			.extracting(item -> Items.ids(item.plan()).getFirst())
			.containsExactly(2L, 1L);
	}

	@Test
	void withoutBigItemsASubscribedRowTakesTwoOfTheFiveSeats() {
		assertThat(ItemRules.TOPIC_SHARE_PERCENT).isEqualTo(40);
		// 큰 항목이 없으면 칸 5 × 40% = 2. 정치 9 · 8 · 7 · 6, 국제 5 · 4, 경제 2 — 정치는 둘까지라 7 · 6 이 헤드라인이고 경제 2 가 주요 뉴스에
		// 오른다
		List<DigestItemPlan> items = List.of(politics(10, 9), politics(11, 8), politics(12, 7), politics(13, 6),
				world(20, 5), world(21, 4), economy(30, 2));

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, THREE_ROWS);

		// 주요 뉴스 · 헤드라인은 각각 순위대로 — 상한으로 내려간 정치 7 · 6 은 헤드라인 앞쪽
		assertThat(arranged).extracting(item -> Items.ids(item.plan()).getFirst(), ArrangedItem::kind)
			.containsExactly(tuple(10L, ItemKind.TOP_STORY), tuple(11L, ItemKind.TOP_STORY),
					tuple(20L, ItemKind.TOP_STORY), tuple(21L, ItemKind.TOP_STORY), tuple(30L, ItemKind.TOP_STORY),
					tuple(12L, ItemKind.HEADLINE), tuple(13L, ItemKind.HEADLINE));
	}

	@Test
	void twoRowsWithItemsAreTooFewForTheCap() {
		// 세 행을 구독했지만 그날 항목은 정치 · 국제 두 행뿐 — 2 × 40% 로는 칸을 다 채울 수 없어 상한을 걸지 않고 순위대로 다섯
		List<DigestItemPlan> items = List.of(politics(10, 9), politics(11, 8), politics(12, 7), politics(13, 6),
				world(20, 5), world(21, 4));

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, THREE_ROWS);

		assertThat(topStories(arranged)).containsExactly(10L, 11L, 12L, 13L, 20L);
		assertThat(headlines(arranged)).containsExactly(21L);
	}

	@Test
	void seatsTheCapLeavesEmptyAreFilledInRankOrder() {
		// 정치 9 · 8 · 7 · 6, 국제 5, 경제 4 — 1차로 정치 9 · 8 · 국제 5 · 경제 4 의 넷, 남은 한 자리는 순위대로 정치 7
		List<DigestItemPlan> items = List.of(politics(10, 9), politics(11, 8), politics(12, 7), politics(13, 6),
				world(20, 5), economy(30, 4));

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, THREE_ROWS);

		assertThat(topStories(arranged)).containsExactly(10L, 11L, 12L, 20L, 30L);
		assertThat(headlines(arranged)).containsExactly(13L);
	}

	@Test
	void anItemOverTwoRowsUsesTheRoomierRowOnly() {
		// 항목 X: 정치 사건 40(7.5)과 국제 사건 41(7) — 정치 몫이 다 찬 뒤라 남은 몫이 큰 국제의 몫 하나를 쓰고 든다. 국제 몫이 하나 남아 국제
		// 6 은 들고 국제 5 는 건너뛴다
		DigestItemPlan x = new DigestItemPlan(
				List.of(subscribed(40, 7.5, Items.POLITICS_EXECUTIVE), subscribed(41, 7.0, "world.conflict")), null,
				null);
		List<DigestItemPlan> items = List.of(politics(10, 9), politics(11, 8), x, world(20, 6), world(21, 5),
				economy(30, 3));

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, THREE_ROWS);

		assertThat(topStories(arranged)).containsExactly(10L, 11L, 40L, 20L, 30L);
		assertThat(headlines(arranged)).containsExactly(21L);
	}

	@Test
	void anItemWithoutASubscribedRowTakesNoQuota() {
		// 주제 구독 밖 관심 흐름의 사건(문화 50, 6)만 든 항목은 어느 행의 몫도 쓰지 않는다
		List<DigestItemPlan> items = List.of(politics(10, 9), politics(11, 8), politics(12, 7),
				solo(followed(50, 6.0, "culture.books")), world(20, 5), economy(30, 4));

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, THREE_ROWS);

		assertThat(topStories(arranged)).containsExactly(10L, 11L, 50L, 20L, 30L);
		assertThat(headlines(arranged)).containsExactly(12L);
	}

	@Test
	void aBigItemUsesTheQuotaOfItsRow() {
		// 큰 항목 하나(정치 1)면 칸 6 × 40% = 2. 큰 항목이 정치 몫 하나를 먼저 써서 큰 슬롯 밖 정치는 9 하나만 들고 8 은 헤드라인이다
		List<DigestItemPlan> items = List.of(new DigestItemPlan(List.of(big(1, 0)), 0, null), politics(10, 9),
				politics(11, 8), world(20, 6), world(21, 5), economy(30, 4), economy(31, 3));

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, THREE_ROWS);

		// 큰 항목 1 과 경제 31 은 같은 점수 3 이라 가장 작은 사건 ID 순
		assertThat(topStories(arranged)).containsExactly(10L, 20L, 21L, 30L, 1L, 31L);
		assertThat(headlines(arranged)).containsExactly(11L);
	}

	@Test
	void theRowOfABigItemCountsTowardTheRows() {
		// 큰 항목은 경제, 큰 슬롯 밖 항목은 정치 · 국제 두 행뿐 — 경제까지 세 행이라 상한(칸 6 × 40% = 2)을 건다. 1차로 정치 9 · 8 · 국제 5 · 4,
		// 남은 한 자리는 순위대로 정치 7 이고 정치 6 은 헤드라인이다
		List<DigestItemPlan> items = List.of(new DigestItemPlan(List.of(big(1, 0, "economy.markets")), 0, null),
				politics(10, 9), politics(11, 8), politics(12, 7), politics(13, 6), world(20, 5), world(21, 4));

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, THREE_ROWS);

		assertThat(topStories(arranged)).containsExactly(10L, 11L, 12L, 20L, 21L, 1L);
		assertThat(headlines(arranged)).containsExactly(13L);
	}

	@Test
	void theCapGrowsWithTheBigItemsOfTheDay() {
		// 구독 밖 주제(문화)의 큰 항목 셋이면 칸 8 × 40% 의 내림 3 — 큰 항목은 몫을 쓰지 않는다. 정치는 9 · 8 · 7 의 셋이 들고 정치 6 · 경제 3 이
		// 헤드라인이다(큰 항목이 없으면 상한 2 라 정치 7 대신 경제 3 이 든다)
		List<DigestItemPlan> items = List.of(new DigestItemPlan(List.of(big(1, 0, "culture.books")), 0, null),
				new DigestItemPlan(List.of(big(2, 1, "culture.books")), 1, null),
				new DigestItemPlan(List.of(big(3, 2, "culture.books")), 2, null), politics(10, 9), politics(11, 8),
				politics(12, 7), politics(13, 6), world(20, 5), economy(30, 4), economy(31, 3));

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, THREE_ROWS);

		assertThat(topStories(arranged)).containsExactly(10L, 11L, 12L, 20L, 30L, 1L, 2L, 3L);
		assertThat(headlines(arranged)).containsExactly(13L, 31L);
	}

	@Test
	void bigItemsCanUseUpTheQuotaOfTheirRowAndTheSeatsAreStillFilled() {
		// 정치 큰 항목 둘이면 칸 7 × 40% = 2 를 큰 항목이 다 쓴다. 1차는 정치 없이 국제 6 · 5 · 경제 4 · 3 의 넷, 남은 한 자리는 순위대로 정치 9
		// 라 정치 8 만 헤드라인이다 — 내림으로 몫의 합(3 행 × 2)이 칸 7 에 못 미치는 날은 2차가 채운다
		List<DigestItemPlan> items = List.of(new DigestItemPlan(List.of(big(1, 0)), 0, null),
				new DigestItemPlan(List.of(big(2, 1)), 1, null), politics(10, 9), politics(11, 8), world(20, 6),
				world(21, 5), economy(30, 4), economy(31, 3));

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL, THREE_ROWS);

		assertThat(topStories(arranged)).containsExactly(10L, 20L, 21L, 30L, 1L, 2L, 31L);
		assertThat(headlines(arranged)).containsExactly(11L);
	}

	@Test
	void anItemNeedsDistinctEvents() {
		SelectedEvent event = subscribed(1);

		assertThatIllegalArgumentException().isThrownBy(() -> new DigestItemPlan(List.of(), null, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestItemPlan(List.of(event, event), null, null));
		assertThat(event.reason()).isEqualTo(SelectionReason.SUBSCRIBED);
	}

	/** 큰 판정 나무 밖 사건 하나의 항목 */
	private static DigestItemPlan solo(SelectedEvent event) {
		return new DigestItemPlan(List.of(event), null, null);
	}

	private static DigestItemPlan politics(long id, double importance) {
		return solo(subscribed(id, importance, Items.POLITICS_EXECUTIVE));
	}

	private static DigestItemPlan world(long id, double importance) {
		return solo(subscribed(id, importance, "world.conflict"));
	}

	private static DigestItemPlan economy(long id, double importance) {
		return solo(subscribed(id, importance, "economy.markets"));
	}

	/** 주요 뉴스 항목의 첫 사건 ID — 순서대로 */
	private static List<Long> topStories(List<ArrangedItem> arranged) {
		return firstEvents(arranged, ItemKind.TOP_STORY);
	}

	/** 헤드라인 항목의 첫 사건 ID — 순서대로 */
	private static List<Long> headlines(List<ArrangedItem> arranged) {
		return firstEvents(arranged, ItemKind.HEADLINE);
	}

	private static List<Long> firstEvents(List<ArrangedItem> arranged, ItemKind kind) {
		return arranged.stream()
			.filter(item -> item.kind() == kind)
			.map(item -> Items.ids(item.plan()).getFirst())
			.toList();
	}

}
