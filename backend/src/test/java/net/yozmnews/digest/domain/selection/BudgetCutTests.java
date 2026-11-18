package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import org.junit.jupiter.api.Test;

/**
 * 입력 토큰 예산 — 추정(코드 포인트 × 1.0 + 후보 줄 80, 지시 2,000 · 프로필 몫(고정 틀 200 포함) · 큰 스토리라인 묶음 몫(이름 + 100)을 뺀
 * 예산), 자르는 순서(구독 꼬리 먼저, 그다음 큰 슬롯 꼬리 — 행 위치도 함께 빠진다), 토큰 어림이 없는 기사는 뺀다
 */
class BudgetCutTests {

	private static final ArticleFacts B1 = Facts.article(1).build();

	private static final ArticleFacts B2 = Facts.article(2).build();

	private static final ArticleFacts S3 = Facts.article(3).build();

	private static final ArticleFacts S4 = Facts.article(4).build();

	@Test
	void aCandidateCostsItsTitleAndSummaryPlusTheLineOverhead() {
		assertThat(CandidateCost.candidate(40, 300)).isEqualTo(420);
		assertThat(CandidateCost.candidate(0, 0)).isEqualTo(80);
	}

	@Test
	void storylineGroupsCostTheirTitlesPlusTheGroupOverhead() {
		// (30 + 100) + (12 + 100) = 242
		assertThat(CandidateCost.storylines(List.of(30, 12))).isEqualTo(242);
		assertThat(CandidateCost.storylines(List.of())).isZero();
		assertThatIllegalArgumentException().isThrownBy(() -> CandidateCost.storylines(List.of(-1)));
	}

	@Test
	void theBudgetLeavesRoomForTheInstructionsTheProfileAndTheStorylineGroups() {
		// 32,000 − 2,000 − (관심사 100 + (구독 행 3 + 제외 지역 2) × 20 + 고정 틀 200) − 묶음 242 = 29,358
		assertThat(CandidateCost.candidateBudget(32_000, 100, 3, 2, 242)).isEqualTo(29_358);
		// 묶음이 없으면 묶음 몫만큼 넓다 — 대조
		assertThat(CandidateCost.candidateBudget(32_000, 100, 3, 2, 0)).isEqualTo(29_600);
		assertThat(CandidateCost.candidateBudget(1_000, 0, 0, 0, 0)).isZero();
		assertThatIllegalArgumentException().isThrownBy(() -> CandidateCost.candidateBudget(32_000, 0, 0, 0, -1));
	}

	@Test
	void subscribedTailGoesFirstThenTheBigSlotTailWithItsRow() {
		Shortlist shortlist = new Shortlist(List.of(B1, B2), List.of(S3, S4), List.of(0, 2));
		Map<ArticleId, Integer> tokens = Map.of(B1.id(), 100, B2.id(), 100, S3.id(), 100, S4.id(), 100);

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
	void anArticleWithoutATokenEstimateIsLeftOutWithItsRow() {
		Shortlist shortlist = new Shortlist(List.of(B1, B2), List.of(S3, S4), List.of(1, 3));

		assertThat(BudgetCut.apply(shortlist, Map.of(B2.id(), 100, S4.id(), 100), 10_000))
			.isEqualTo(new Shortlist(List.of(B2), List.of(S4), List.of(3)));
	}

	@Test
	void aShortlistNeedsOneRowPerBigArticle() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Shortlist(List.of(B1, B2), List.of(), List.of(0)));
	}

}
