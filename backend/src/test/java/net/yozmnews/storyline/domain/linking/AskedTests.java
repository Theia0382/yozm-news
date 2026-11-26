package net.yozmnews.storyline.domain.linking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * 띠 안 후보 하나에 물은 결과 — 확률은 0 ~ 1 의 유한값이고(없을 수 있다), 받지 않은 값은 문구에 칸 이름을 싣는다.
 * <p>
 * 지키는 규칙: storyline-R36
 */
class AskedTests {

	@Test
	void acceptsTheEndsOfTheProbabilityRangeAndNoProbability() {
		assertThat(new Asked(0.0, 7L).probability()).isEqualTo(0.0);
		assertThat(new Asked(1.0, 7L).probability()).isEqualTo(1.0);
		assertThat(Asked.UNANSWERED.answered()).isFalse();
	}

	@Test
	void refusesAProbabilityOutsideTheRangeNamingIt() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Asked(1.1, 7L))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — Asked.probability: 1.1");
		assertThatIllegalArgumentException().isThrownBy(() -> new Asked(Double.NaN, 7L))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — Asked.probability: NaN");
		assertThatIllegalArgumentException().isThrownBy(() -> new Asked(Double.NEGATIVE_INFINITY, 7L))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — Asked.probability: -Infinity");
	}

}
