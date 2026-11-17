package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import org.junit.jupiter.api.Test;

/**
 * 입력 토큰 예산 — 추정(코드 포인트 × 1.0 + 후보 줄 80, 지시 2,000 · 프로필 몫을 뺀 예산), 자르는 순서(구독 꼬리 먼저, 그다음 큰 슬롯 꼬리),
 * 토큰 어림이 없는 기사는 뺀다
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
	void theBudgetLeavesRoomForTheInstructionsAndTheProfile() {
		// 32,000 − 2,000 − (관심사 100 + (구독 행 3 + 제외 지역 2) × 20) = 29,800
		assertThat(CandidateCost.candidateBudget(32_000, 100, 3, 2)).isEqualTo(29_800);
		assertThat(CandidateCost.candidateBudget(1_000, 0, 0, 0)).isZero();
	}

	@Test
	void subscribedTailGoesFirstThenTheBigSlotTail() {
		Shortlist shortlist = new Shortlist(List.of(B1, B2), List.of(S3, S4));
		Map<ArticleId, Integer> tokens = Map.of(B1.id(), 100, B2.id(), 100, S3.id(), 100, S4.id(), 100);

		assertThat(BudgetCut.apply(shortlist, tokens, 400)).isEqualTo(shortlist);
		assertThat(BudgetCut.apply(shortlist, tokens, 399)).isEqualTo(new Shortlist(List.of(B1, B2), List.of(S3)));
		assertThat(BudgetCut.apply(shortlist, tokens, 200)).isEqualTo(new Shortlist(List.of(B1, B2), List.of()));
		assertThat(BudgetCut.apply(shortlist, tokens, 199)).isEqualTo(new Shortlist(List.of(B1), List.of()));
		assertThat(BudgetCut.apply(shortlist, tokens, 0)).isEqualTo(new Shortlist(List.of(), List.of()));
	}

	@Test
	void anArticleWithoutATokenEstimateIsLeftOut() {
		Shortlist shortlist = new Shortlist(List.of(B1), List.of(S3, S4));

		assertThat(BudgetCut.apply(shortlist, Map.of(B1.id(), 100, S4.id(), 100), 10_000))
			.isEqualTo(new Shortlist(List.of(B1), List.of(S4)));
	}

}
