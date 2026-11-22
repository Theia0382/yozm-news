package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.digest.domain.selection.BigSlotPicker.BigEvent;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigRow;
import net.yozmnews.digest.domain.selection.BigSlotPicker.BigSlots;
import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;
import org.junit.jupiter.api.Test;

/**
 * 큰 슬롯 — 큰 슬롯 수만큼 행(후보 사건이 없는 행은 세지 않음), 행마다 중요도 합 상위 10 사건(후보 없는 · 앞 행이 쓴 사건은 세지 않음),
 * 상한(사건 수)을 넘으면 행마다 고르게, 큰 슬롯 0 은 끔
 */
class BigSlotPickerTests {

	/** 거른 꼭대기가 아닌 행의 머리 */
	private static final StorylineId HEAD = new StorylineId(500);

	/** 거른 꼭대기가 없는 필터 */
	private static final UserFilter NO_EXCLUSIONS = Facts.politicsAndMarkets();

	@Test
	void rowsAreTakenInOrderUpToTheUsersBigSlotsSkippingRowsWithoutCandidates() {
		BigRow first = new BigRow(HEAD, HEAD, List.of(event(1, "5")));
		BigRow nothing = new BigRow(HEAD, HEAD, List.of(new BigEvent(new EventId(2), new BigDecimal("9"), null)));
		BigRow second = new BigRow(HEAD, HEAD, List.of(event(3, "4")));
		BigRow third = new BigRow(HEAD, HEAD, List.of(event(4, "3")));

		BigSlots slots = BigSlotPicker.pick(List.of(first, nothing, second, third), 2, 30, NO_EXCLUSIONS);

		assertThat(ids(slots)).containsExactly(1L, 3L);
		// 행 위치는 입력 행의 위치 — 후보 없는 행(위치 1)을 건너뛰어도 셋째 행은 위치 2
		assertThat(slots.rows()).containsExactly(0, 2);
	}

	@Test
	void aRowWhoseHeadIsAnExcludedTopIsSkippedAndKeepsItsPosition() {
		StorylineId excluded = new StorylineId(100);
		BigRow first = new BigRow(excluded, excluded, List.of(event(1, "9")));
		BigRow second = new BigRow(HEAD, HEAD, List.of(event(2, "8")));
		BigRow third = new BigRow(new StorylineId(300), new StorylineId(300), List.of(event(3, "7")));
		// 대조 — 거른 꼭대기가 없으면 첫 행부터
		assertThat(BigSlotPicker.pick(List.of(first, second, third), 2, 30, NO_EXCLUSIONS).rows()).containsExactly(0, 1);

		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of(), Set.of(), Set.of()), Set.of(),
				Set.of(excluded));
		BigSlots slots = BigSlotPicker.pick(List.of(first, second, third), 2, 30, filter);

		assertThat(ids(slots)).containsExactly(2L, 3L);
		assertThat(slots.rows()).containsExactly(1, 2);
	}

	@Test
	void theRowIsJudgedByTheTopOfItsHeadNotByTheHead() {
		StorylineId excludedTop = new StorylineId(100);
		StorylineId excludedHead = new StorylineId(200);
		// 머리 201 의 꼭대기가 거른 꼭대기 100 — 건너뛴다. 머리 200 은 거른 꼭대기지만 그 꼭대기 300 은 아니다 — 쓴다
		BigRow underExcludedTop = new BigRow(new StorylineId(201), excludedTop, List.of(event(1, "9")));
		BigRow excludedHeadOnly = new BigRow(excludedHead, new StorylineId(300), List.of(event(2, "8")));
		UserFilter filter = UserFilter.of(Facts.interests(List.of(), Set.of(), Set.of(), Set.of()), Set.of(),
				Set.of(excludedTop, excludedHead));

		BigSlots slots = BigSlotPicker.pick(List.of(underExcludedTop, excludedHeadOnly), 2, 30, filter);

		assertThat(ids(slots)).containsExactly(2L);
		assertThat(slots.rows()).containsExactly(1);
	}

	@Test
	void aResolvedRowTakesTheTopOfItsHeadOrTheHeadItselfWhenTheTopIsUnknown() {
		StorylineId head = new StorylineId(201);
		StorylineId top = new StorylineId(100);
		StorylineId unknown = new StorylineId(202);
		Map<StorylineId, StorylineId> tops = Map.of(head, top);

		assertThat(BigRow.resolved(head, tops, List.of()).top()).isEqualTo(top);
		assertThat(BigRow.resolved(unknown, tops, List.of()).top()).isEqualTo(unknown);
	}

	@Test
	void eachRowGivesItsTenEventsWithTheLargestImportanceSum() {
		List<BigEvent> events = new ArrayList<>();
		for (int i = 1; i <= 11; i++) {
			events.add(event(i, String.valueOf(i)));
		}
		// 같은 중요도 합 11 이 하나 더 — 사건 ID 가 작은 쪽이 먼저(사건 12 > 사건 11)
		events.add(event(12, "11"));

		BigSlots slots = BigSlotPicker.pick(List.of(new BigRow(HEAD, HEAD, events)), 3, 30, NO_EXCLUSIONS);

		// 11(ID 11) · 11(ID 12) · 10 · 9 · … · 3 — 중요도 합 2 · 1 은 빠진다
		assertThat(ids(slots)).containsExactly(11L, 12L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L);
	}

	@Test
	void eventsWithoutCandidatesOrUsedByAnEarlierRowDoNotCount() {
		BigRow first = new BigRow(HEAD, HEAD, List.of(event(1, "9"), event(2, "8")));
		// 둘째 행: 사건 1 은 첫 행이 썼고 사건 5 는 후보가 없다 — k 를 세지 않고 다음 사건으로
		List<BigEvent> secondEvents = new ArrayList<>(
				List.of(event(1, "20"), new BigEvent(new EventId(5), new BigDecimal("19"), null)));
		for (int i = 6; i <= 16; i++) {
			secondEvents.add(event(i, "1"));
		}

		BigSlots slots = BigSlotPicker.pick(List.of(first, new BigRow(HEAD, HEAD, secondEvents)), 2, 30, NO_EXCLUSIONS);

		// 첫 행 1 · 2, 둘째 행은 사건 6 ~ 15 의 열 개(16 은 빠진다)
		assertThat(ids(slots)).containsExactly(1L, 2L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
	}

	@Test
	void overTheCapEachRowIsTrimmedEvenlyAndTheCapCountsEvents() {
		// 사건마다 기사 셋 — 상한은 사건 수라 기사 수와 무관하다
		BigRow first = new BigRow(HEAD, HEAD, List.of(event(1, "9", 3), event(2, "8", 3), event(3, "7", 3)));
		BigRow second = new BigRow(HEAD, HEAD, List.of(event(4, "9", 3)));
		BigRow third = new BigRow(HEAD, HEAD, List.of(event(5, "9", 3), event(6, "8", 3), event(7, "7", 3)));

		// 상한 5 — 행 순서로 하나씩: 1 · 4 · 5 · 2 · 6
		BigSlots slots = BigSlotPicker.pick(List.of(first, second, third), 3, 5, NO_EXCLUSIONS);

		assertThat(ids(slots)).containsExactly(1L, 2L, 4L, 5L, 6L);
		// 줄인 뒤에도 행 위치가 사건과 같은 순서로 남는다
		assertThat(slots.rows()).containsExactly(0, 0, 1, 2, 2);
		assertThat(slots.events().getFirst().articles()).hasSize(3);
	}

	@Test
	void zeroBigSlotsTurnTheSlotOff() {
		BigRow row = new BigRow(HEAD, HEAD, List.of(event(1, "9")));

		assertThat(BigSlotPicker.pick(List.of(row), 0, 30, NO_EXCLUSIONS)).isEqualTo(BigSlots.NONE);
		assertThat(BigSlotPicker.pick(List.of(), 3, 30, NO_EXCLUSIONS).events()).isEmpty();
		assertThat(BigSlotPicker.pick(List.of(row), 3, 0, NO_EXCLUSIONS).events()).isEmpty();
		assertThat(BigSlotPicker.pick(List.of(row), 3, 0, NO_EXCLUSIONS).rows()).isEmpty();
	}

	@Test
	void slotsNeedOneRowPerEventAndACandidateOfTheSameEvent() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BigSlots(List.of(Facts.single(Facts.article(1))), List.of()));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new BigEvent(new EventId(2), BigDecimal.ONE, Facts.single(Facts.article(1))));
	}

	@Test
	void theCapIsHalfOfTheMaximumRoundedDown() {
		assertThat(SelectionRules.bigSeats(60)).isEqualTo(30);
		assertThat(SelectionRules.bigSeats(45)).isEqualTo(22);
		assertThat(SelectionRules.bigSeats(1)).isZero();
	}

	/** 기사 하나뿐인 후보 사건을 가진 큰 판정 행의 사건 */
	private static BigEvent event(long id, String importanceSum) {
		return event(id, importanceSum, 1);
	}

	/** 기사 여럿인 후보 사건을 가진 큰 판정 행의 사건 — 기사 ID 는 사건 ID × 100 + 1 … */
	private static BigEvent event(long id, String importanceSum, int articles) {
		Facts.Builder[] builders = new Facts.Builder[articles];
		for (int i = 0; i < articles; i++) {
			builders[i] = Facts.article(id * 100 + i + 1);
		}
		return new BigEvent(new EventId(id), new BigDecimal(importanceSum), Facts.event(id, builders));
	}

	private static List<Long> ids(BigSlots slots) {
		return slots.events().stream().map(event -> event.facts().id().value()).toList();
	}

}
