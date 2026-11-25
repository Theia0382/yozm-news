package net.yozmnews.report.domain.prompt;

import static net.yozmnews.report.ReportFixtures.coverage;
import static net.yozmnews.report.ReportFixtures.interests;
import static net.yozmnews.report.ReportFixtures.userStoryline;
import static net.yozmnews.report.ReportFixtures.userStorylines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.TokenEstimate;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.domain.alias.ReportAliases;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.report.ReportPeriod;
import org.junit.jupiter.api.Test;

/**
 * 리포트 입력의 예산 — docs/specs/report.md §5 · report-R22
 */
class ReportInputBudgetTests {

	private static LlmModel model(int budget, boolean structured) {
		return new LlmModel(Provider.ANTHROPIC, "claude-test", budget, structured);
	}

	@Test
	void capsPerPeriodKind() {
		assertThat(ReportInputBudget.cap(ReportPeriodKind.WEEK)).isEqualTo(80_000);
		assertThat(ReportInputBudget.cap(ReportPeriodKind.MONTH)).isEqualTo(160_000);
	}

	@Test
	void userContentIsTheSmallerCapLessTheInstructionReserve() {
		// 모델 예산 120,000 — 주간은 리포트 상한 80,000, 월간은 모델 예산이 작다
		assertThat(ReportInputBudget.userContent(model(120_000, true), ReportPeriodKind.WEEK)).isEqualTo(72_000);
		assertThat(ReportInputBudget.userContent(model(120_000, true), ReportPeriodKind.MONTH)).isEqualTo(112_000);
		// 모델 예산이 작으면 모델 예산
		assertThat(ReportInputBudget.userContent(model(50_000, true), ReportPeriodKind.WEEK)).isEqualTo(42_000);
		// 지시 몫보다 작으면 0
		assertThat(ReportInputBudget.userContent(model(5_000, true), ReportPeriodKind.MONTH)).isZero();
	}

	@Test
	void priorShareIsThirtyPercentRoundedDown() {
		assertThat(ReportInputBudget.priorShare(72_000)).isEqualTo(21_600);
		assertThat(ReportInputBudget.priorShare(10)).isEqualTo(3);
		assertThat(ReportInputBudget.priorShare(0)).isZero();
		assertThatThrownBy(() -> ReportInputBudget.priorShare(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * 지시 몫이 실제 지시를 덮는다 — 갈래 셋 · 월간(가장 긴 길이 기준) · 구조화 출력이 아니면 지시에 붙는 스키마 · 다시 묻는 문장과 가장 긴 진단.
	 * 구조화 출력이어도 스키마는 공급자가 입력으로 셀 수 있어 함께 센다
	 */
	@Test
	void instructionReserveCoversTheLongestInstructions() {
		ReportPeriod month = new ReportPeriod(ReportPeriodKind.MONTH, ReportFixtures.PERIOD.start(),
				ReportFixtures.PERIOD.start().plusSeconds(31L * 24 * 3600));
		ReportInput input = new ReportInput(ReportFixtures.USER, month, ReportFixtures.ZONE,
				List.of(userStorylines(userStoryline(31, 902)), coverage(), interests(31)), List.of(), ReportFixtures.SNAPSHOT, null);
		String diagnosis = "가".repeat(ReportPromptRules.MAX_DIAGNOSIS);
		for (boolean structured : new boolean[] { true, false }) {
			ReportPrompt first = ReportPrompts.compose(input, ReportAliases.of(input), model(120_000, structured));
			ReportPrompt retry = ReportPrompts.retry(first, diagnosis);
			int schema = structured ? codePoints(retry.outputSchema()) : 0;
			assertThat(retry.instructions()).contains(diagnosis);
			assertThat(TokenEstimate.tokens(codePoints(retry.instructions()) + schema)).as("구조화 출력 %s", structured)
				.isLessThanOrEqualTo(ReportInputBudget.INSTRUCTION_RESERVE);
		}
	}

	private static int codePoints(String text) {
		return text.codePointCount(0, text.length());
	}

}
