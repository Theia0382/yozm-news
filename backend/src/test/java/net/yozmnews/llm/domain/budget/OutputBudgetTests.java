package net.yozmnews.llm.domain.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.yozmnews.llm.ReasoningEffort;
import org.junit.jupiter.api.Test;

/**
 * 공급자에 보내는 출력 토큰 상한 — 본문 몫에 추론 강도의 여유를 더한다.
 * <p>
 * 지키는 규칙: llm-R23
 */
class OutputBudgetTests {

	@Test
	void addsTheAllowanceOfTheEffort() {
		assertThat(OutputBudget.total(1_000, ReasoningEffort.LOW)).isEqualTo(5_000);
		assertThat(OutputBudget.total(1_000, ReasoningEffort.MEDIUM)).isEqualTo(9_000);
		assertThat(OutputBudget.total(32_000, ReasoningEffort.HIGH)).isEqualTo(48_000);
		assertThat(OutputBudget.total(1, ReasoningEffort.LOW)).isEqualTo(4_001);
	}

	@Test
	void refusesAnEmptyBody() {
		assertThatThrownBy(() -> OutputBudget.total(0, ReasoningEffort.LOW))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
