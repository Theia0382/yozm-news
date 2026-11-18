package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.yozmnews.digest.domain.selection.BigSlotPicker.BigEvent;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigRow;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigSlots;
import net.yozmnews.event.EventId;
import org.junit.jupiter.api.Test;

/**
 * 큰 슬롯 — 큰 슬롯 수만큼 행(대표 없는 행은 세지 않음), 행마다 중요도 합 상위 10 사건(대표 없는 · 앞 행이 쓴 사건은 세지 않음), 상한을
 * 넘으면 행마다 고르게, 큰 슬롯 0 은 끔
 */
class BigSlotPickerTests {

	@Test
	void rowsAreTakenInOrderUpToTheUsersBigSlotsSkippingRowsWithoutRepresentatives() {
		BigRow first = new BigRow(List.of(event(1, "5", 101)));
		BigRow nothing = new BigRow(List.of(new BigEvent(new EventId(2), new BigDecimal("9"), null)));
		BigRow second = new BigRow(List.of(event(3, "4", 103)));
		BigRow third = new BigRow(List.of(event(4, "3", 104)));

		BigSlots slots = BigSlotPicker.pick(List.of(first, nothing, second, third), 2, 30);

		assertThat(ids(slots)).containsExactly(101L, 103L);
		// 행 위치는 입력 행의 위치 — 대표 없는 행(위치 1)을 건너뛰어도 셋째 행은 위치 2
		assertThat(slots.rows()).containsExactly(0, 2);
		assertThat(slots.events()).containsExactlyInAnyOrder(new EventId(1), new EventId(3));
	}

	@Test
	void eachRowGivesItsTenEventsWithTheLargestImportanceSum() {
		List<BigEvent> events = new ArrayList<>();
		for (int i = 1; i <= 11; i++) {
			events.add(event(i, String.valueOf(i), 100 + i));
		}
		// 같은 중요도 합 11 이 하나 더 — 사건 ID 가 작은 쪽이 먼저(사건 12 > 사건 11)
		events.add(event(12, "11", 112));

		BigSlots slots = BigSlotPicker.pick(List.of(new BigRow(events)), 3, 30);

		// 11(ID 11) · 11(ID 12) · 10 · 9 · … · 3 — 중요도 합 2 · 1 은 빠진다
		assertThat(ids(slots)).containsExactly(111L, 112L, 110L, 109L, 108L, 107L, 106L, 105L, 104L, 103L);
	}

	@Test
	void eventsWithoutRepresentativesOrUsedByAnEarlierRowDoNotCount() {
		BigRow first = new BigRow(List.of(event(1, "9", 101), event(2, "8", 102)));
		// 둘째 행: 사건 1 은 첫 행이 썼고 사건 5 는 대표가 없다 — k 를 세지 않고 다음 사건으로
		List<BigEvent> secondEvents = new ArrayList<>(List.of(event(1, "20", 101),
				new BigEvent(new EventId(5), new BigDecimal("19"), null)));
		for (int i = 6; i <= 16; i++) {
			secondEvents.add(event(i, "1", 100 + i));
		}

		BigSlots slots = BigSlotPicker.pick(List.of(first, new BigRow(secondEvents)), 2, 30);

		// 첫 행 101 · 102, 둘째 행은 사건 6 ~ 15 의 열 개(16 은 빠진다)
		assertThat(ids(slots)).containsExactly(101L, 102L, 106L, 107L, 108L, 109L, 110L, 111L, 112L, 113L, 114L,
				115L);
	}

	@Test
	void overTheCapEachRowIsTrimmedEvenly() {
		BigRow first = new BigRow(List.of(event(1, "9", 101), event(2, "8", 102), event(3, "7", 103)));
		BigRow second = new BigRow(List.of(event(4, "9", 104)));
		BigRow third = new BigRow(List.of(event(5, "9", 105), event(6, "8", 106), event(7, "7", 107)));

		// 상한 5 — 행 순서로 하나씩: 101 · 104 · 105 · 102 · 106
		BigSlots slots = BigSlotPicker.pick(List.of(first, second, third), 3, 5);

		assertThat(ids(slots)).containsExactly(101L, 102L, 104L, 105L, 106L);
		// 줄인 뒤에도 행 위치가 기사와 같은 순서로 남는다
		assertThat(slots.rows()).containsExactly(0, 0, 1, 2, 2);
		assertThat(slots.events()).containsExactlyInAnyOrder(new EventId(1), new EventId(2), new EventId(4),
				new EventId(5), new EventId(6));
	}

	@Test
	void zeroBigSlotsTurnTheSlotOff() {
		BigRow row = new BigRow(List.of(event(1, "9", 101)));

		assertThat(BigSlotPicker.pick(List.of(row), 0, 30)).isEqualTo(BigSlots.NONE);
		assertThat(BigSlotPicker.pick(List.of(), 3, 30).articles()).isEmpty();
		assertThat(BigSlotPicker.pick(List.of(row), 3, 0).events()).isEqualTo(Set.of());
		assertThat(BigSlotPicker.pick(List.of(row), 3, 0).rows()).isEmpty();
	}

	@Test
	void slotsNeedOneRowPerArticle() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BigSlots(List.of(Facts.article(1).build()), List.of(), Set.of(new EventId(1))));
	}

	@Test
	void theCapIsHalfOfTheMaximumRoundedDown() {
		assertThat(SelectionRules.bigSeats(60)).isEqualTo(30);
		assertThat(SelectionRules.bigSeats(45)).isEqualTo(22);
		assertThat(SelectionRules.bigSeats(1)).isZero();
	}

	private static BigEvent event(long id, String importanceSum, long article) {
		return new BigEvent(new EventId(id), new BigDecimal(importanceSum), Facts.article(article).build());
	}

	private static List<Long> ids(BigSlots slots) {
		return slots.articles().stream().map(article -> article.id().value()).toList();
	}

}
