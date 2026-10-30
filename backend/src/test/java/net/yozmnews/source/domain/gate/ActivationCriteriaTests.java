package net.yozmnews.source.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.source.domain.feed.SkipReason;
import net.yozmnews.source.domain.feed.SkippedItem;
import org.junit.jupiter.api.Test;

/**
 * 활성화 기준의 경계 — 기준마다 경계 값은 통과하고 한 칸 넘으면 그 기준 하나만 못 미친다. 미리보기는 지표만 손으로 넣어 만들고, 기대값은 사람이 계산한
 * 비율 리터럴이다.
 * <p>
 * 지키는 규칙: source-R9
 */
class ActivationCriteriaTests {

	@Test
	void needsAtLeastOneArticle() {
		assertThat(ActivationCriteria.judge(preview(1, 0, 1, false, 0, 100))).isEmpty();
		assertThat(ActivationCriteria.judge(preview(0, 0, 0, false, 0, 100)))
			.containsExactly(new Shortfall(Criterion.ARTICLES, 0.0, 1.0));
	}

	@Test
	void allowsATenthOfTheItemsToBeSkipped() {
		// 기사 9 + 건너뜀 1 = 1/10
		assertThat(ActivationCriteria.judge(preview(9, 1, 9, false, 0, 100))).isEmpty();
		// 기사 8 + 건너뜀 1 = 1/9
		assertThat(ActivationCriteria.judge(preview(8, 1, 8, false, 0, 100)))
			.containsExactly(new Shortfall(Criterion.SKIPPED_ITEMS, 1.0 / 9, 0.1));
	}

	@Test
	void needsNineTenthsOfTheArticlesDated() {
		assertThat(ActivationCriteria.judge(preview(10, 0, 9, false, 0, 100))).isEmpty();
		assertThat(ActivationCriteria.judge(preview(10, 0, 8, false, 0, 100)))
			.containsExactly(new Shortfall(Criterion.DATED_ARTICLES, 0.8, 0.9));
	}

	@Test
	void skipsTheDateCriterionWhenTheProfileReadsNoPublishedAt() {
		assertThat(ActivationCriteria.judge(preview(10, 0, 0, true, 0, 100))).isEmpty();
		// 대조 — 선언하지 않은 같은 피드는 날짜 기준에 못 미친다
		assertThat(ActivationCriteria.judge(preview(10, 0, 0, false, 0, 100)))
			.containsExactly(new Shortfall(Criterion.DATED_ARTICLES, 0.0, 0.9));
	}

	@Test
	void allowsAThousandthOfTheCharactersReplaced() {
		assertThat(ActivationCriteria.judge(preview(1, 0, 1, false, 1, 1_000))).isEmpty();
		assertThat(ActivationCriteria.judge(preview(1, 0, 1, false, 2, 1_000)))
			.containsExactly(new Shortfall(Criterion.REPLACED_CHARACTERS, 0.002, 0.001));
	}

	@Test
	void judgesNoRatioWithoutItsDenominator() {
		// 항목 0 · 기사 0 · 읽은 문자 0 — 비율은 판정하지 않고 기사 수만 못 미친다
		assertThat(ActivationCriteria.judge(preview(0, 0, 0, false, 0, 0)))
			.containsExactly(new Shortfall(Criterion.ARTICLES, 0.0, 1.0));
		// 기사 0 · 건너뜀 2 — 건너뜀의 분모는 항목 수라 판정한다. 날짜는 분모(기사)가 0 이라 판정하지 않는다
		assertThat(ActivationCriteria.judge(preview(0, 2, 0, false, 0, 100))).containsExactly(
				new Shortfall(Criterion.ARTICLES, 0.0, 1.0), new Shortfall(Criterion.SKIPPED_ITEMS, 1.0, 0.1));
	}

	@Test
	void collectsEveryShortfallInCriterionOrder() {
		// 기사 1 · 건너뜀 1 = 1/2, 해석 0/1, 대체 5/100
		assertThat(ActivationCriteria.judge(preview(1, 1, 0, false, 5, 100))).containsExactly(
				new Shortfall(Criterion.SKIPPED_ITEMS, 0.5, 0.1), new Shortfall(Criterion.DATED_ARTICLES, 0.0, 0.9),
				new Shortfall(Criterion.REPLACED_CHARACTERS, 0.05, 0.001));
	}

	private static FeedPreview preview(int articles, int skipped, int dated, boolean publishedAtUnread, int replaced,
			int decoded) {
		List<SkippedItem> items = new ArrayList<>();
		for (int i = 0; i < skipped; i++) {
			items.add(new SkippedItem(articles + i + 1, SkipReason.MISSING_LINK));
		}
		return new FeedPreview(articles, items, dated, publishedAtUnread, new SummaryLengths(0, articles, null, null,
				null), replaced, decoded, null, null, List.of());
	}

}
