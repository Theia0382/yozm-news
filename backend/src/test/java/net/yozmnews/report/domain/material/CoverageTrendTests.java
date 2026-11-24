package net.yozmnews.report.domain.material;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.report.domain.material.CoverageTrend.Trend;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 보도량 판정 — 손으로 쓴 경계 표. 지키는 규칙: report-R15
 */
class CoverageTrendTests {

	@ParameterizedTest(name = "{0} → {1} = {2}")
	@CsvSource(nullValues = "null", value = {
			"null, 5, NO_COMPARISON",
			"0, 3, NEW",
			"0, 2, STEADY",
			"10, 15, ROSE",
			"10, 14, STEADY",
			"1, 3, STEADY",
			"10, 5, FELL",
			"10, 6, STEADY",
			"2, 0, STEADY",
			"3, 0, FELL" })
	void judgesTheChangeInTheRuleOrder(Long previous, long current, Trend expected) {
		assertThat(CoverageTrend.of(current, previous)).isEqualTo(expected);
	}

}
