package net.yozmnews.storyline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 큰 판정 기준의 범위 — 보도 강도 하한은 0 보다 크고 9999.99 이하 · 소수 둘째 자리까지, 둘째 자리로 맞춘 값.
 * <p>
 * 지키는 규칙: storyline-R20
 */
class BigStorylineCriteriaTests {

	@Test
	void acceptsTheBoundsAndKeepsTheIntensityAtTwoDecimals() {
		assertThat(new BigStorylineCriteria(new BigDecimal("0.01")).minIntensity()).hasToString("0.01");
		assertThat(new BigStorylineCriteria(new BigDecimal("9999.99")).minIntensity()).hasToString("9999.99");
		assertThat(new BigStorylineCriteria(new BigDecimal("15")).minIntensity()).hasToString("15.00");
		assertThat(new BigStorylineCriteria(new BigDecimal("7.5")).minIntensity()).hasToString("7.50");
		// 값을 바꾸지 않는 셋째 자리 0 은 받는다 — 같은 값이다
		assertThat(new BigStorylineCriteria(new BigDecimal("15.000"))).isEqualTo(new BigStorylineCriteria(new BigDecimal("15")));
	}

	@Test
	void rejectsValuesOutOfRange() {
		assertThatIllegalArgumentException().isThrownBy(() -> new BigStorylineCriteria(new BigDecimal("0")));
		assertThatIllegalArgumentException().isThrownBy(() -> new BigStorylineCriteria(new BigDecimal("-1")));
		assertThatIllegalArgumentException().isThrownBy(() -> new BigStorylineCriteria(new BigDecimal("10000")));
		assertThatIllegalArgumentException().isThrownBy(() -> new BigStorylineCriteria(new BigDecimal("15.001")));
		assertThat(BigStorylineCriteria.of(new BigDecimal("0"))).isEmpty();
		assertThat(BigStorylineCriteria.of(new BigDecimal("15.001"))).isEmpty();
		assertThat(BigStorylineCriteria.of(new BigDecimal("20.5"))).contains(new BigStorylineCriteria(new BigDecimal("20.50")));
	}

	@Test
	void theFirstValueIsIntensityFifteen() {
		assertThat(BigStorylineCriteria.DEFAULT).isEqualTo(new BigStorylineCriteria(new BigDecimal("15.00")));
	}

}
