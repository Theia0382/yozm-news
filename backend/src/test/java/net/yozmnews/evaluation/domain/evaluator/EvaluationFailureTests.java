package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import net.yozmnews.evaluation.EvaluatorFailureKind;
import org.junit.jupiter.api.Test;

/**
 * 예상한 평가 실패 — 갈래와 문구
 */
class EvaluationFailureTests {

	@Test
	void hasThreeKindsInDeclaredOrder() {
		assertThat(EvaluatorFailureKind.values()).containsExactly(EvaluatorFailureKind.TRANSIENT,
				EvaluatorFailureKind.ARTICLE_REJECTED, EvaluatorFailureKind.EVALUATOR_UNAVAILABLE);
	}

	@Test
	void refusesABlankMessage() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new EvaluationFailure(EvaluatorFailureKind.TRANSIENT, " "));
		assertThat(new EvaluationFailure(EvaluatorFailureKind.TRANSIENT, "마감 초과").message()).isEqualTo("마감 초과");
	}

}
