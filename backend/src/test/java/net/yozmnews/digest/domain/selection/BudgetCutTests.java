package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.event.EventId;
import org.junit.jupiter.api.Test;

/**
 * 입력 토큰 예산 — 추정(기사 줄 = 코드 포인트 × 1.0 + 80, 사건 = 기사 줄의 합 + 40, 지시 6,000 · 프로필 몫(고정 틀 200 포함)을 뺀 예산),
 * 자르는 걸음(하나를 뺀 뒤 큰 슬롯 사건이 남는 사건의 절반보다 많으면 큰 슬롯에서 행마다 고르게, 아니면 구독 사건의 꼬리에서 — 행 위치도 함께
 * 빠진다), 토큰 어림이 없는 사건은 뺀다. 기대값은 손으로 센 리터럴이다
 */
class BudgetCutTests {

	private static final CandidateEvent B1 = Facts.single(Facts.article(1));

	private static final CandidateEvent B2 = Facts.single(Facts.article(2));

	private static final CandidateEvent S3 = Facts.single(Facts.article(3));

	private static final CandidateEvent S4 = Facts.single(Facts.article(4));

	@Test
	void anArticleLineCostsItsTitleAndSummaryPlusTheLineOverhead() {
		assertThat(CandidateCost.candidate(40, 300)).isEqualTo(420);
		assertThat(CandidateCost.candidate(0, 0)).isEqualTo(80);
	}

	@Test
	void anEventCostsItsArticleLinesPlusTheEventOverhead() {
		// 420 + 80 + 40 = 540
		assertThat(CandidateCost.event(List.of(420, 80))).isEqualTo(540);
		assertThat(CandidateCost.event(List.of(80))).isEqualTo(120);
		assertThatIllegalArgumentException().isThrownBy(() -> CandidateCost.event(List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> CandidateCost.event(List.of(-1)));
	}

	@Test
	void theBudgetLeavesRoomForTheInstructionsAndTheProfile() {
		// 32,000 − 6,000 − ((구독 행 3 + 제외 지역 2) × 20 + 고정 틀 200) = 25,700
		assertThat(CandidateCost.candidateBudget(32_000, 3, 2)).isEqualTo(25_700);
		assertThat(CandidateCost.candidateBudget(1_000, 0, 0)).isZero();
	}

	@Test
	void eachStepCutsTheBigSlotWhileItIsOverHalfOfWhatIsLeftAndOtherwiseTheSubscribedTail() {
		Shortlist shortlist = new Shortlist(List.of(B1, B2), List.of(S3, S4), List.of(), List.of(0, 2));
		Map<EventId, Integer> tokens = Map.of(id(B1), 100, id(B2), 100, id(S3), 100, id(S4), 100);

		assertThat(BudgetCut.apply(shortlist, tokens, 400)).isEqualTo(shortlist);
		// 남는 3 의 절반 1 < 큰 2 — 큰 슬롯에서, 행마다 하나씩이라 뒤 행 B2
		assertThat(BudgetCut.apply(shortlist, tokens, 399))
			.isEqualTo(new Shortlist(List.of(B1), List.of(S3, S4), List.of(), List.of(0)));
		// 다음 걸음은 남는 2 의 절반 1 = 큰 1 — 구독 꼬리 S4
		assertThat(BudgetCut.apply(shortlist, tokens, 200))
			.isEqualTo(new Shortlist(List.of(B1), List.of(S3), List.of(), List.of(0)));
		// 남는 1 의 절반 0 < 큰 1 — 큰 슬롯 B1
		assertThat(BudgetCut.apply(shortlist, tokens, 199))
			.isEqualTo(new Shortlist(List.of(), List.of(S3), List.of(), List.of()));
		assertThat(BudgetCut.apply(shortlist, tokens, 0))
			.isEqualTo(new Shortlist(List.of(), List.of(), List.of(), List.of()));
	}

	@Test
	void theSubscribedTailGoesBeforeTheFollowedEvents() {
		// 구독 S3 · S4, 새 길 F5 · F6 — 사건마다 100. 구독 꼬리가 먼저 빠지고, 구독이 다 빠진 뒤에 새 길 꼬리
		CandidateEvent f5 = Facts.single(Facts.article(5));
		CandidateEvent f6 = Facts.single(Facts.article(6));
		Shortlist shortlist = new Shortlist(List.of(), List.of(S3, S4), List.of(f5, f6), List.of());
		Map<EventId, Integer> tokens = Map.of(id(S3), 100, id(S4), 100, id(f5), 100, id(f6), 100);

		// 대조 — 예산 안이면 그대로
		assertThat(BudgetCut.apply(shortlist, tokens, 400)).isEqualTo(shortlist);
		assertThat(BudgetCut.apply(shortlist, tokens, 200))
			.isEqualTo(new Shortlist(List.of(), List.of(), List.of(f5, f6), List.of()));
		assertThat(BudgetCut.apply(shortlist, tokens, 100))
			.isEqualTo(new Shortlist(List.of(), List.of(), List.of(f5), List.of()));
	}

	@Test
	void followedEventsCountAmongWhatIsLeftForTheBigSlotHalf() {
		// 큰 B1 · B2, 새 길 F5 · F6 — 400 → 300: 남는 3 의 절반 1 < 큰 2 라 큰 슬롯에서(새 길도 남는 사건으로 센다)
		CandidateEvent f5 = Facts.single(Facts.article(5));
		CandidateEvent f6 = Facts.single(Facts.article(6));
		Shortlist shortlist = new Shortlist(List.of(B1, B2), List.of(), List.of(f5, f6), List.of(0, 2));
		Map<EventId, Integer> tokens = Map.of(id(B1), 100, id(B2), 100, id(f5), 100, id(f6), 100);

		assertThat(BudgetCut.apply(shortlist, tokens, 300))
			.isEqualTo(new Shortlist(List.of(B1), List.of(), List.of(f5, f6), List.of(0)));
	}

	@Test
	void theBigSlotKeepsAtMostHalfOfWhatIsLeftWhenSubscribedEventsAreCut() {
		// 큰 슬롯 행 셋(행 0 · 1 · 2 에 넷씩) · 구독 12 · 사건마다 100 · 예산 10 개 — 큰 7 · 구독 7 이 번갈아 빠져 큰 5(2 · 2 · 1) · 구독 5
		List<CandidateEvent> big = events(100, 12);
		List<CandidateEvent> subscribed = events(200, 12);
		Shortlist shortlist = new Shortlist(big, subscribed, List.of(), List.of(0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2));

		assertThat(BudgetCut.apply(shortlist, hundredEach(big, subscribed), 1_000)).isEqualTo(new Shortlist(
				List.of(big.get(0), big.get(1), big.get(4), big.get(5), big.get(8)), subscribed.subList(0, 5), List.of(),
				List.of(0, 0, 1, 1, 2)));
	}

	@Test
	void theBigSlotUsesWhatIsLeftWhenSubscribedEventsRunShort() {
		// 큰 12(행마다 넷) · 구독 2 · 예산 10 개 — 구독 2 는 남고 큰 슬롯이 남는 예산을 쓴다: 큰 8(3 · 3 · 2) · 구독 2
		List<CandidateEvent> big = events(100, 12);
		List<CandidateEvent> subscribed = events(200, 2);
		Shortlist shortlist = new Shortlist(big, subscribed, List.of(), List.of(0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2));

		assertThat(BudgetCut.apply(shortlist, hundredEach(big, subscribed), 1_000)).isEqualTo(new Shortlist(
				List.of(big.get(0), big.get(1), big.get(2), big.get(4), big.get(5), big.get(6), big.get(8),
						big.get(9)),
				subscribed, List.of(), List.of(0, 0, 0, 1, 1, 1, 2, 2)));
	}

	@Test
	void theBigSlotAloneIsCutEvenlyAcrossRows() {
		// 행 0 에 둘(B1 · B2) · 행 2 에 하나(S3 를 큰 슬롯으로) — 하나 빼기는 가장 많이 남은 행 0 의 마지막 B2
		Shortlist shortlist = new Shortlist(List.of(B1, B2, S3), List.of(), List.of(), List.of(0, 0, 2));
		Map<EventId, Integer> tokens = Map.of(id(B1), 100, id(B2), 100, id(S3), 100);

		assertThat(BudgetCut.apply(shortlist, tokens, 200))
			.isEqualTo(new Shortlist(List.of(B1, S3), List.of(), List.of(), List.of(0, 2)));
	}

	@Test
	void subscribedEventsAloneAreCutFromTheTail() {
		Shortlist shortlist = new Shortlist(List.of(), List.of(S3, S4), List.of(), List.of());
		Map<EventId, Integer> tokens = Map.of(id(S3), 100, id(S4), 100);

		assertThat(BudgetCut.apply(shortlist, tokens, 100)).isEqualTo(new Shortlist(List.of(), List.of(S3), List.of(), List.of()));
	}

	@Test
	void oneBigAndOneSubscribedEventInARoomForOneKeepsTheSubscribedEvent() {
		Shortlist shortlist = new Shortlist(List.of(B1), List.of(S3), List.of(), List.of(0));
		Map<EventId, Integer> tokens = Map.of(id(B1), 100, id(S3), 100);

		assertThat(BudgetCut.apply(shortlist, tokens, 100)).isEqualTo(new Shortlist(List.of(), List.of(S3), List.of(), List.of()));
	}

	@Test
	void anEventWithoutATokenEstimateIsLeftOutWithItsRow() {
		Shortlist shortlist = new Shortlist(List.of(B1, B2), List.of(S3, S4), List.of(), List.of(1, 3));

		assertThat(BudgetCut.apply(shortlist, Map.of(id(B2), 100, id(S4), 100), 10_000))
			.isEqualTo(new Shortlist(List.of(B2), List.of(S4), List.of(), List.of(3)));
	}

	@Test
	void aShortlistNeedsOneRowPerBigEvent() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Shortlist(List.of(B1, B2), List.of(), List.of(), List.of(0)));
	}

	/** 기사 {@code from} 부터 하나씩 사건 {@code count} 개 */
	private static List<CandidateEvent> events(long from, int count) {
		List<CandidateEvent> events = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			events.add(Facts.single(Facts.article(from + i)));
		}
		return events;
	}

	private static Map<EventId, Integer> hundredEach(List<CandidateEvent> big, List<CandidateEvent> subscribed) {
		Map<EventId, Integer> tokens = new HashMap<>();
		big.forEach(event -> tokens.put(id(event), 100));
		subscribed.forEach(event -> tokens.put(id(event), 100));
		return tokens;
	}

	private static EventId id(CandidateEvent event) {
		return event.facts().id();
	}

}
