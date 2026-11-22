package net.yozmnews.report.domain.generator;

import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.section;
import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.llm.GenerationStop;
import net.yozmnews.platform.OutputShape;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.alias.ReportAliases;
import net.yozmnews.report.domain.output.ReportOutputSchema;
import org.junit.jupiter.api.Test;

/**
 * 생성 한 번의 판정 — 끝까지 쓴 응답만 읽고, 잘림 · 거절 · 생성기 실패는 그 코드.
 * <p>
 * 지키는 규칙: report-R8
 */
class ReportVerdictTests {

	private static final OutputShape.Obj ROOT = ReportOutputSchema
		.root(ReportAliases.of(input(section(ReportSectionKind.INTERESTS))));

	private static final String READABLE = """
			{"overview":"개요","sections":{"interests":{"title":"관심","body":"본문","refs":[]}}}""";

	@Test
	void readsACompleteAnswer() {
		ReportVerdict verdict = ReportVerdict.judge(generated(READABLE, GenerationStop.COMPLETE), ROOT);

		assertThat(verdict).isInstanceOf(ReportVerdict.Read.class);
		assertThat(((ReportVerdict.Read) verdict).output().draft().overview()).isEqualTo("개요");
	}

	@Test
	void aCompleteAnswerThatCannotBeReadIsUnreadableWithTheDiagnosis() {
		assertThat(ReportVerdict.judge(generated("{\"overview\":\"개요\"}", GenerationStop.COMPLETE), ROOT))
			.isEqualTo(new ReportVerdict.Fail(ReportFailure.UNREADABLE_OUTPUT, "$.sections: 없다"));
	}

	@Test
	void doesNotReadATruncatedOrRefusedAnswerEvenWhenItsBodyIsReadable() {
		assertThat(ReportVerdict.judge(generated(READABLE, GenerationStop.TRUNCATED), ROOT))
			.isEqualTo(new ReportVerdict.Fail(ReportFailure.OUTPUT_TRUNCATED, "출력이 출력 토큰 상한에 닿아 잘렸다"));
		assertThat(ReportVerdict.judge(generated(READABLE, GenerationStop.REFUSED), ROOT))
			.isEqualTo(new ReportVerdict.Fail(ReportFailure.OUTPUT_REFUSED, "모델이 출력을 거절했다"));
	}

	@Test
	void keepsAGeneratorFailureAsItIs() {
		assertThat(ReportVerdict.judge(new ReportOutcome.Failed(ReportFailure.NO_GENERATOR, "없다"), ROOT))
			.isEqualTo(new ReportVerdict.Fail(ReportFailure.NO_GENERATOR, "없다"));
	}

	private static ReportOutcome generated(String text, GenerationStop stop) {
		return new ReportOutcome.Generated(text, new TokenUsage(10, 20), stop);
	}

}
