package net.yozmnews.digest.domain.item;

import static net.yozmnews.digest.domain.item.Items.big;
import static net.yozmnews.digest.domain.item.Items.ids;
import static net.yozmnews.digest.domain.item.Items.storyline;
import static net.yozmnews.digest.domain.item.Items.subscribed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;

import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;
import org.junit.jupiter.api.Test;

/**
 * 항목 묶기 — 같은 꼭대기의 사건은 한 항목, 꼭대기가 없으면 혼자, 큰 슬롯 사건은 그 행의 항목, 꼭대기가 여럿이면 큰 판정 나무 → 고른 사건이
 * 많은 꼭대기 → 꼭대기 조회의 순서. 큰 판정 나무는 자르기 뒤 큰 슬롯 사건이 남은 행의 나무뿐이다
 * <p>
 * 지키는 규칙: digest-R46
 */
class ItemGroupingTests {

	@Test
	void eventsOfOneTreeMakeOneItemAndAnEventWithoutATreeStandsAlone() {
		Map<EventId, List<StorylineId>> tops = Map.of(new EventId(1), List.of(storyline(100)), new EventId(2),
				List.of(storyline(100)), new EventId(3), List.of(storyline(200)));

		List<DigestItemPlan> items = ItemGrouping.group(
				List.of(subscribed(1), subscribed(4), subscribed(2), subscribed(3)), tops, Map.of());

		// 항목은 첫 사건이 나온 순서 — 1 · 2 는 나무 100, 4 는 꼭대기가 없어 혼자, 3 은 나무 200
		assertThat(items).extracting(Items::ids).containsExactly(List.of(1L, 2L), List.of(4L), List.of(3L));
		assertThat(items).extracting(DigestItemPlan::bigRow).containsOnlyNulls();
	}

	@Test
	void bigSlotEventsGoToTheItemOfTheirRowAndASubscribedEventOfThatTreeJoinsIt() {
		// 큰 판정 행 2 의 머리는 꼭대기 100 — 큰 슬롯 사건 1 · 2 와 그 나무의 구독 사건 3 이 한 항목이다. 큰 슬롯 사건 2 는 꼭대기 조회에
		// 없어도 그 행의 항목에 든다
		Map<EventId, List<StorylineId>> tops = Map.of(new EventId(1), List.of(storyline(100)), new EventId(3),
				List.of(storyline(100)), new EventId(4), List.of(storyline(300)));

		List<DigestItemPlan> items = ItemGrouping.group(List.of(big(1, 2), big(2, 2), subscribed(3), subscribed(4)),
				tops, Map.of(2, storyline(100)));

		assertThat(items).extracting(Items::ids).containsExactly(List.of(1L, 2L, 3L), List.of(4L));
		assertThat(items).extracting(DigestItemPlan::bigRow).containsExactly(2, null);
	}

	@Test
	void aTreeOfABigRowThatWasNotPickedIsAnOrdinaryItem() {
		// 꼭대기 100 은 큰 판정 목록에 있지만 큰 슬롯 사건이 남지 않은 행이라 bigHeads 에 없다 — 그 나무의 구독 사건은 bigRow 가 없다
		Map<EventId, List<StorylineId>> tops = Map.of(new EventId(3), List.of(storyline(100)));

		List<DigestItemPlan> items = ItemGrouping.group(List.of(big(1, 0), subscribed(3)), tops,
				Map.of(0, storyline(900)));

		assertThat(items).extracting(Items::ids).containsExactly(List.of(1L), List.of(3L));
		assertThat(items).extracting(DigestItemPlan::bigRow).containsExactly(0, null);
	}

	@Test
	void amongSeveralTopsTheBigTreeComesFirst() {
		// 사건 3 의 꼭대기는 200 · 100 순 — 100 이 큰 판정 나무라 앞선다
		Map<EventId, List<StorylineId>> tops = Map.of(new EventId(1), List.of(storyline(100)), new EventId(3),
				List.of(storyline(200), storyline(100)));

		List<DigestItemPlan> items = ItemGrouping.group(List.of(big(1, 0), subscribed(3)), tops,
				Map.of(0, storyline(100)));
		assertThat(items).extracting(Items::ids).containsExactly(List.of(1L, 3L));

		assertThat(items).extracting(DigestItemPlan::bigRow).containsExactly(0);

		// 대조 — 같은 꼭대기에서 행 0 의 머리가 다른 꼭대기(900)면 100 은 큰 판정 나무가 아니다. 사건 3 은 100 의 일반 항목이고(고른
		// 사건 가운데 100 을 가진 사건이 둘이라 200 보다 앞선다) 큰 슬롯 항목과 갈린다
		List<DigestItemPlan> notBig = ItemGrouping.group(List.of(big(1, 0), subscribed(3)), tops,
				Map.of(0, storyline(900)));
		assertThat(notBig).extracting(Items::ids).containsExactly(List.of(1L), List.of(3L));
		assertThat(notBig).extracting(DigestItemPlan::bigRow).containsExactly(0, null);
	}

	@Test
	void amongSeveralTopsTheTopHeldByMoreSelectedEventsComesNext() {
		// 사건 3 의 꼭대기는 100 · 200 순, 사건 5 는 200 만 — 200 을 가진 고른 사건이 둘이라 3 은 200 의 항목
		List<DigestItemPlan> items = ItemGrouping.group(List.of(subscribed(3), subscribed(5)),
				Map.of(new EventId(3), List.of(storyline(100), storyline(200)), new EventId(5), List.of(storyline(200))),
				Map.of());
		assertThat(items).extracting(Items::ids).containsExactly(List.of(3L, 5L));

		// 대조 — 사건 5 가 300 이면 100 · 200 이 각 하나라 꼭대기 조회의 순서로 100
		List<DigestItemPlan> tie = ItemGrouping.group(List.of(subscribed(3), subscribed(5)),
				Map.of(new EventId(3), List.of(storyline(100), storyline(200)), new EventId(5), List.of(storyline(300))),
				Map.of());
		assertThat(tie).extracting(Items::ids).containsExactly(List.of(3L), List.of(5L));
	}

	@Test
	void amongSeveralTopsTheOrderOfTheTopsDecidesLast() {
		// 사건 8 은 100 만, 사건 9 는 200 만 — 100 · 200 을 가진 고른 사건이 각 둘이라 앞 두 열쇠가 같다. 사건 3 은 꼭대기 조회의 순서
		// (소속 점수 순)로 앞선 꼭대기의 항목
		List<DigestItemPlan> first200 = ItemGrouping.group(List.of(subscribed(8), subscribed(9), subscribed(3)),
				Map.of(new EventId(3), List.of(storyline(200), storyline(100)), new EventId(8), List.of(storyline(100)),
						new EventId(9), List.of(storyline(200))),
				Map.of());
		assertThat(first200).extracting(Items::ids).containsExactly(List.of(8L), List.of(9L, 3L));

		// 대조 — 순서만 바꾸면 100 의 항목
		List<DigestItemPlan> first100 = ItemGrouping.group(List.of(subscribed(8), subscribed(9), subscribed(3)),
				Map.of(new EventId(3), List.of(storyline(100), storyline(200)), new EventId(8), List.of(storyline(100)),
						new EventId(9), List.of(storyline(200))),
				Map.of());
		assertThat(first100).extracting(Items::ids).containsExactly(List.of(8L, 3L), List.of(9L));
	}

	@Test
	void aBigSlotEventWhoseRowHasNoHeadIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> ItemGrouping.group(List.of(big(1, 0)), Map.of(), Map.of()));
	}

}
