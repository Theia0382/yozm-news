package net.yozmnews.storyline.domain.relation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 관계 기준의 범위 — 합치기 확률 기준은 확률 기준보다 낮을 수 없다(합치기는 한 방향이다).
 * <p>
 * 지키는 규칙: storyline-R17 · storyline-R29
 */
class RelationCriteriaTests {

	private static final Duration WINDOW = Duration.ofDays(14);

	@Test
	void theMergeProbabilityIsBetweenTheProbabilityAndOne() {
		assertThat(new RelationCriteria(0.60, 0.20, 3, 0.88, WINDOW, 0.60).mergeProbability()).isEqualTo(0.60);
		assertThat(new RelationCriteria(0.60, 0.20, 3, 0.88, WINDOW, 1.0).mergeProbability()).isEqualTo(1.0);
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationCriteria(0.60, 0.20, 3, 0.88, WINDOW, 0.59))
			.withMessage("합치기 확률 기준은 확률 기준 0.6 이상 1 이하다: 0.59");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationCriteria(0.60, 0.20, 3, 0.88, WINDOW, 1.01))
			.withMessage("합치기 확률 기준은 확률 기준 0.6 이상 1 이하다: 1.01");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationCriteria(0.60, 0.20, 3, 0.88, WINDOW, Double.NaN))
			.withMessageStartingWith("합치기 확률 기준은");
	}

	@Test
	void theOtherRangesStay() {
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationCriteria(0, 0.20, 3, 0.88, WINDOW, 0.60))
			.withMessage("확률 기준은 0 보다 크고 1 이하다: 0.0");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationCriteria(0.60, 0.20, 1, 0.88, WINDOW, 0.60))
			.withMessage("사건 수 하한은 2 이상이다: 1");
	}

}
