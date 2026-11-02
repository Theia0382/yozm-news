package net.yozmnews.evaluation.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import net.yozmnews.evaluation.application.EvaluationRound;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 평가 작업을 만들 때의 경고 — 평가기가 없는 구성에서만 한 줄 남긴다. 회차는 평가기가 있는지만 답하는 대역이다(평가 흐름은
 * {@code EvaluationFlowTests}).
 * <p>
 * 지키는 규칙: evaluation-R20
 */
@ExtendWith(OutputCaptureExtension.class)
class EvaluatePendingArticlesJobTests {

	private static final String WARNING = "평가기가 없어 기사를 평가하지 않는다 — 기사는 평가 대기에 쌓인다";

	@Test
	void warnsOnceWhenThereIsNoEvaluator(CapturedOutput output) {
		EvaluationRound round = mock(EvaluationRound.class);
		given(round.evaluatorAvailable()).willReturn(false);

		new EvaluatePendingArticlesJob(round);

		assertThat(output.getOut().split(WARNING, -1)).hasSize(2);
	}

	@Test
	void staysQuietWithAnEvaluator(CapturedOutput output) {
		EvaluationRound round = mock(EvaluationRound.class);
		given(round.evaluatorAvailable()).willReturn(true);

		new EvaluatePendingArticlesJob(round);

		assertThat(output).doesNotContain(WARNING);
	}

}
