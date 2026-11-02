package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * 예상한 평가 실패 — 갈래와 문구
 */
class EvaluationFailureTests {

	@Test
	void hasThreeKindsInDeclaredOrder() {
		assertThat(EvaluationFailure.Kind.values()).containsExactly(EvaluationFailure.Kind.TRANSIENT,
				EvaluationFailure.Kind.ARTICLE_REJECTED, EvaluationFailure.Kind.EVALUATOR_UNAVAILABLE);
	}

	@Test
	void refusesABlankMessage() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EvaluationFailure(EvaluationFailure.Kind.TRANSIENT, " "));
		assertThat(new EvaluationFailure(EvaluationFailure.Kind.TRANSIENT, "마감 초과").message()).isEqualTo("마감 초과");
	}

}
