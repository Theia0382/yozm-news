package net.yozmnews.digest.domain.item;

import static net.yozmnews.digest.domain.item.Items.big;
import static net.yozmnews.digest.domain.item.Items.subscribed;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.interest.InterestWeights;
import net.yozmnews.digest.domain.interest.StorylineInterests;
import net.yozmnews.digest.domain.interest.TopicInterests;
import net.yozmnews.digest.domain.selection.SelectedEvent;
import org.junit.jupiter.api.Test;

/**
 * 항목 순위와 가르기 — 항목 점수 = 사건 점수의 최대(없으면 맨 뒤), 같으면 가장 작은 사건 ID. 큰 판정 나무의 항목은 모두 주요 뉴스, 나머지는
 * 5 개까지 주요 뉴스, 그 뒤 헤드라인. 결과는 주요 뉴스 다음 헤드라인
 * <p>
 * 지키는 규칙: digest-R47
 */
class ItemPlanTests {

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

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL);

		// 주요 뉴스: 9 · 8 · 7 · 6 · 5 다음 큰 항목 둘(3 — 사건 1 · 2 순)은 순위대로 섞이고, 4 · 2 는 헤드라인
		assertThat(arranged).extracting(item -> Items.ids(item.plan()).getFirst(), ArrangedItem::kind)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(10L, ItemKind.TOP_STORY),
					org.assertj.core.groups.Tuple.tuple(11L, ItemKind.TOP_STORY),
					org.assertj.core.groups.Tuple.tuple(12L, ItemKind.TOP_STORY),
					org.assertj.core.groups.Tuple.tuple(13L, ItemKind.TOP_STORY),
					org.assertj.core.groups.Tuple.tuple(14L, ItemKind.TOP_STORY),
					org.assertj.core.groups.Tuple.tuple(1L, ItemKind.TOP_STORY),
					org.assertj.core.groups.Tuple.tuple(2L, ItemKind.TOP_STORY),
					org.assertj.core.groups.Tuple.tuple(15L, ItemKind.HEADLINE),
					org.assertj.core.groups.Tuple.tuple(16L, ItemKind.HEADLINE));
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

		List<ArrangedItem> arranged = ItemPlan.arrange(items, InterestWeights.NEUTRAL_ALL);

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

		List<ArrangedItem> arranged = ItemPlan.arrange(List.of(c, b, a), InterestWeights.NEUTRAL_ALL);

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

		assertThat(ItemPlan.arrange(List.of(later, earlier), InterestWeights.NEUTRAL_ALL))
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
		assertThat(ItemPlan.arrange(List.of(politics3, politics4), interests))
			.extracting(item -> Items.ids(item.plan()).getFirst())
			.containsExactly(2L, 1L);
	}

	@Test
	void anItemNeedsDistinctEvents() {
		SelectedEvent event = subscribed(1);

		org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
			.isThrownBy(() -> new DigestItemPlan(List.of(), null, null));
		org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
			.isThrownBy(() -> new DigestItemPlan(List.of(event, event), null, null));
		assertThat(event.reason()).isEqualTo(SelectionReason.SUBSCRIBED);
	}

}
