package net.yozmnews.report.domain.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.domain.prompt.ReportPromptRules.Range;
import org.junit.jupiter.api.Test;

/**
 * 프롬프트 구성의 상수 · 출력 본문 토큰 — docs/specs/report.md §5 · report-R23 · report-R24
 */
class ReportPromptRulesTests {

	@Test
	void bodyTokensCoverTheDescribedMaximaPerSection() {
		// 개요 1,500 + 갈래마다 (제목 200 + 본문 3,000 + 별칭 1,000) + JSON 500
		assertThat(ReportPromptRules.bodyTokens(1)).isEqualTo(6_200);
		assertThat(ReportPromptRules.bodyTokens(2)).isEqualTo(10_400);
		assertThat(ReportPromptRules.bodyTokens(3)).isEqualTo(14_600);
	}

	@Test
	void refusesSectionCountsOutsideOneToThree() {
		assertThatThrownBy(() -> ReportPromptRules.bodyTokens(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ReportPromptRules.bodyTokens(4)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void lengthTargetsPerPeriodKind() {
		assertThat(ReportPromptRules.overview(ReportPeriodKind.WEEK)).isEqualTo(new Range(300, 600));
		assertThat(ReportPromptRules.body(ReportPeriodKind.WEEK)).isEqualTo(new Range(300, 900));
		assertThat(ReportPromptRules.overview(ReportPeriodKind.MONTH)).isEqualTo(new Range(600, 1_200));
		assertThat(ReportPromptRules.body(ReportPeriodKind.MONTH)).isEqualTo(new Range(600, 1_800));
		assertThat(ReportPromptRules.TITLE_LENGTH).isEqualTo(40);
	}

	@Test
	void rangeRefusesAnEmptyOrInvertedRange() {
		assertThatThrownBy(() -> new Range(0, 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Range(10, 9)).isInstanceOf(IllegalArgumentException.class);
	}

}
