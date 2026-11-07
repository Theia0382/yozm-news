package net.yozmnews.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 큰 사건 기준의 범위 — 소스 하한 1 이상, 중요도 하한 1 ~ 5 · 소수 둘째 자리까지, 둘째 자리로 맞춘 값.
 * <p>
 * 지키는 규칙: event-R21
 */
class BigEventCriteriaTests {

	@Test
	void acceptsTheBoundsAndKeepsTheImportanceAtTwoDecimals() {
		assertThat(new BigEventCriteria(1, new BigDecimal("1")).minImportance()).hasToString("1.00");
		assertThat(new BigEventCriteria(1, new BigDecimal("1.00")).minImportance()).hasToString("1.00");
		assertThat(new BigEventCriteria(7, new BigDecimal("5.00")).minImportance()).hasToString("5.00");
		assertThat(new BigEventCriteria(3, new BigDecimal("3.1")).minImportance()).hasToString("3.10");
		// 값을 바꾸지 않는 셋째 자리 0 은 받는다 — 같은 값이다
		assertThat(new BigEventCriteria(3, new BigDecimal("3.100"))).isEqualTo(new BigEventCriteria(3, new BigDecimal("3.1")));
	}

	@Test
	void rejectsValuesOutOfRange() {
		assertThatIllegalArgumentException().isThrownBy(() -> new BigEventCriteria(0, new BigDecimal("3.00")));
		assertThatIllegalArgumentException().isThrownBy(() -> new BigEventCriteria(3, new BigDecimal("0.99")));
		assertThatIllegalArgumentException().isThrownBy(() -> new BigEventCriteria(3, new BigDecimal("5.01")));
		assertThatIllegalArgumentException().isThrownBy(() -> new BigEventCriteria(3, new BigDecimal("3.001")));
		assertThat(BigEventCriteria.of(0, new BigDecimal("3.00"))).isEmpty();
		assertThat(BigEventCriteria.of(3, new BigDecimal("3.001"))).isEmpty();
		assertThat(BigEventCriteria.of(3, new BigDecimal("3.5"))).contains(new BigEventCriteria(3, new BigDecimal("3.50")));
	}

	@Test
	void theFirstValueIsThreeSourcesAndImportanceThree() {
		assertThat(BigEventCriteria.DEFAULT).isEqualTo(new BigEventCriteria(3, new BigDecimal("3.00")));
	}

}
