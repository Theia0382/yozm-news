package net.yozmnews.storyline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 큰 판정 기준의 범위 — 소스 하한 1 이상, 중요도 하한 1 ~ 5 · 소수 둘째 자리까지, 둘째 자리로 맞춘 값.
 * <p>
 * 지키는 규칙: storyline-R20
 */
class BigStorylineCriteriaTests {

	@Test
	void acceptsTheBoundsAndKeepsTheImportanceAtTwoDecimals() {
		assertThat(new BigStorylineCriteria(1, new BigDecimal("1")).minImportance()).hasToString("1.00");
		assertThat(new BigStorylineCriteria(1, new BigDecimal("1.00")).minImportance()).hasToString("1.00");
		assertThat(new BigStorylineCriteria(7, new BigDecimal("5.00")).minImportance()).hasToString("5.00");
		assertThat(new BigStorylineCriteria(3, new BigDecimal("3.1")).minImportance()).hasToString("3.10");
		// 값을 바꾸지 않는 셋째 자리 0 은 받는다 — 같은 값이다
		assertThat(new BigStorylineCriteria(3, new BigDecimal("3.100"))).isEqualTo(new BigStorylineCriteria(3, new BigDecimal("3.1")));
	}

	@Test
	void rejectsValuesOutOfRange() {
		assertThatIllegalArgumentException().isThrownBy(() -> new BigStorylineCriteria(0, new BigDecimal("3.00")));
		assertThatIllegalArgumentException().isThrownBy(() -> new BigStorylineCriteria(3, new BigDecimal("0.99")));
		assertThatIllegalArgumentException().isThrownBy(() -> new BigStorylineCriteria(3, new BigDecimal("5.01")));
		assertThatIllegalArgumentException().isThrownBy(() -> new BigStorylineCriteria(3, new BigDecimal("3.001")));
		assertThat(BigStorylineCriteria.of(0, new BigDecimal("3.00"))).isEmpty();
		assertThat(BigStorylineCriteria.of(3, new BigDecimal("3.001"))).isEmpty();
		assertThat(BigStorylineCriteria.of(3, new BigDecimal("3.5"))).contains(new BigStorylineCriteria(3, new BigDecimal("3.50")));
	}

	@Test
	void theFirstValueIsThreeSourcesAndImportanceThree() {
		assertThat(BigStorylineCriteria.DEFAULT).isEqualTo(new BigStorylineCriteria(3, new BigDecimal("3.00")));
	}

}
