package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;

import net.yozmnews.event.EventId;
import org.junit.jupiter.api.Test;

/**
 * 입력 토큰 예산 — 추정(기사 줄 = 코드 포인트 × 1.0 + 80, 사건 = 기사 줄의 합 + 40, 지시 6,000 · 프로필 몫(고정 틀 200 포함)을 뺀 예산),
 * 자르는 순서(구독 사건 꼬리 먼저, 그다음 큰 슬롯 꼬리 — 행 위치도 함께 빠진다), 토큰 어림이 없는 사건은 뺀다
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
		// 32,000 − 6,000 − (관심사 100 + (구독 행 3 + 제외 지역 2) × 20 + 고정 틀 200) = 25,600
		assertThat(CandidateCost.candidateBudget(32_000, 100, 3, 2)).isEqualTo(25_600);
		assertThat(CandidateCost.candidateBudget(1_000, 0, 0, 0)).isZero();
	}

	@Test
	void subscribedTailGoesFirstThenTheBigSlotTailWithItsRow() {
		Shortlist shortlist = new Shortlist(List.of(B1, B2), List.of(S3, S4), List.of(0, 2));
		Map<EventId, Integer> tokens = Map.of(id(B1), 100, id(B2), 100, id(S3), 100, id(S4), 100);

		assertThat(BudgetCut.apply(shortlist, tokens, 400)).isEqualTo(shortlist);
		assertThat(BudgetCut.apply(shortlist, tokens, 399))
			.isEqualTo(new Shortlist(List.of(B1, B2), List.of(S3), List.of(0, 2)));
		assertThat(BudgetCut.apply(shortlist, tokens, 200))
			.isEqualTo(new Shortlist(List.of(B1, B2), List.of(), List.of(0, 2)));
		assertThat(BudgetCut.apply(shortlist, tokens, 199))
			.isEqualTo(new Shortlist(List.of(B1), List.of(), List.of(0)));
		assertThat(BudgetCut.apply(shortlist, tokens, 0)).isEqualTo(new Shortlist(List.of(), List.of(), List.of()));
	}

	@Test
	void anEventWithoutATokenEstimateIsLeftOutWithItsRow() {
		Shortlist shortlist = new Shortlist(List.of(B1, B2), List.of(S3, S4), List.of(1, 3));

		assertThat(BudgetCut.apply(shortlist, Map.of(id(B2), 100, id(S4), 100), 10_000))
			.isEqualTo(new Shortlist(List.of(B2), List.of(S4), List.of(3)));
	}

	@Test
	void aShortlistNeedsOneRowPerBigEvent() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Shortlist(List.of(B1, B2), List.of(), List.of(0)));
	}

	private static EventId id(CandidateEvent event) {
		return event.facts().id();
	}

}
