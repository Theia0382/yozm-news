package net.yozmnews.report.domain.generator;

import static net.yozmnews.report.ReportFixtures.coverage;
import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.interests;
import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.llm.GenerationStop;
import net.yozmnews.platform.OutputShape;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.domain.alias.ReportAliases;
import net.yozmnews.report.domain.output.ReportOutputSchema;
import org.junit.jupiter.api.Test;

/**
 * 생성 판정과 다시 묻기 — 끝까지 쓴 응답만 읽고, 읽지 못하거나 갈래가 빠지면 한 번 다시 묻는다. 잘림 · 거절 · 생성기 실패는 다시 묻지 않는다.
 * <p>
 * 지키는 규칙: report-R8
 */
class ReportVerdictTests {

	/** 갈래 둘 — 구독 주제 보도량 · 관심 변화 */
	private static final ReportAliases ALIASES = ReportAliases.of(input(coverage(), interests()));

	private static final OutputShape.Obj ROOT = ReportOutputSchema.root(ALIASES);

	private static final String BOTH = """
			{"overview":"개요","sections":{"coverage":{"title":"보도량","body":"본문","refs":[]},\
			"interests":{"title":"관심","body":"본문","refs":[]}}}""";

	private static final String INTERESTS_ONLY = """
			{"overview":"개요 하나","sections":{"interests":{"title":"관심","body":"본문","refs":[]}}}""";

	private static final String NONE = """
			{"overview":"개요 없음","sections":{}}""";

	private static final String UNREADABLE = "{\"overview\":\"개요\"}";

	@Test
	void readsACompleteAnswerWithEverySection() {
		ReportVerdict.Verdict verdict = ReportVerdict.judge(generated(BOTH, GenerationStop.COMPLETE), ROOT, ALIASES);

		assertThat(verdict).isInstanceOfSatisfying(ReportVerdict.Read.class,
				read -> assertThat(read.output().draft().overview()).isEqualTo("개요"));
	}

	@Test
	void asksAgainWithTheDiagnosisWhenACompleteAnswerCannotBeRead() {
		assertThat(ReportVerdict.judge(generated(UNREADABLE, GenerationStop.COMPLETE), ROOT, ALIASES))
			.isEqualTo(new ReportVerdict.Retry("$.sections: 없다"));
	}

	@Test
	void asksAgainWithTheMissingSectionKeysInInputOrder() {
		ReportVerdict.Verdict verdict = ReportVerdict.judge(generated(NONE, GenerationStop.COMPLETE), ROOT, ALIASES);

		assertThat(verdict).isInstanceOfSatisfying(ReportVerdict.RetryMissing.class, retry -> {
			assertThat(retry.diagnosis()).isEqualTo("출력에 없는 갈래: coverage, interests");
			assertThat(retry.missing()).isEqualTo(2);
			assertThat(retry.first().output().draft().overview()).isEqualTo("개요 없음");
		});
	}

	@Test
	void doesNotAskAgainForATruncatedRefusedOrFailedAnswer() {
		assertThat(ReportVerdict.judge(generated(BOTH, GenerationStop.TRUNCATED), ROOT, ALIASES))
			.isEqualTo(new ReportVerdict.Fail(ReportFailure.OUTPUT_TRUNCATED, "출력이 출력 토큰 상한에 닿아 잘렸다"));
		assertThat(ReportVerdict.judge(generated(BOTH, GenerationStop.REFUSED), ROOT, ALIASES))
			.isEqualTo(new ReportVerdict.Fail(ReportFailure.OUTPUT_REFUSED, "모델이 출력을 거절했다"));
		assertThat(ReportVerdict.judge(new ReportOutcome.Failed(ReportFailure.OUTPUT_REFUSED, "공급자 문구"), ROOT, ALIASES))
			.isEqualTo(new ReportVerdict.Fail(ReportFailure.OUTPUT_REFUSED, "공급자 문구"));
	}

	@Test
	void anAnswerAskedAgainAfterAnUnreadableOneFailsWhenItCannotBeReadEither() {
		assertThat(ReportVerdict.settle(generated(UNREADABLE, GenerationStop.COMPLETE), ROOT))
			.isEqualTo(new ReportVerdict.Fail(ReportFailure.UNREADABLE_OUTPUT, "$.sections: 없다"));
		assertThat(ReportVerdict.settle(generated(INTERESTS_ONLY, GenerationStop.COMPLETE), ROOT))
			.isInstanceOfSatisfying(ReportVerdict.Read.class,
					read -> assertThat(read.output().draft().overview()).isEqualTo("개요 하나"));
		assertThat(ReportVerdict.settle(generated(BOTH, GenerationStop.TRUNCATED), ROOT))
			.isEqualTo(new ReportVerdict.Fail(ReportFailure.OUTPUT_TRUNCATED, "출력이 출력 토큰 상한에 닿아 잘렸다"));
		assertThat(ReportVerdict.settle(new ReportOutcome.Failed(ReportFailure.OUTPUT_REFUSED, "없다"), ROOT))
			.isEqualTo(new ReportVerdict.Fail(ReportFailure.OUTPUT_REFUSED, "없다"));
	}

	@Test
	void anAnswerAskedAgainForMissingSectionsKeepsTheReadingWithFewerMissing() {
		ReportVerdict.Read first = firstReading(NONE);

		// 더 적게 빠짐 — 다시 물은 쪽
		assertThat(overview(ReportVerdict.settle(generated(INTERESTS_ONLY, GenerationStop.COMPLETE), ROOT, ALIASES, first)))
			.isEqualTo("개요 하나");
		// 같은 수 — 다시 물은 쪽
		assertThat(overview(ReportVerdict.settle(generated(NONE.replace("개요 없음", "다시 쓴 개요"), GenerationStop.COMPLETE),
				ROOT, ALIASES, first)))
			.isEqualTo("다시 쓴 개요");
		// 더 많이 빠짐 — 첫 읽기
		ReportVerdict.Read oneMissing = firstReading(INTERESTS_ONLY);
		assertThat(ReportVerdict.settle(generated(NONE, GenerationStop.COMPLETE), ROOT, ALIASES, oneMissing))
			.isSameAs(oneMissing);
	}

	@Test
	void anAnswerAskedAgainForMissingSectionsFallsBackToTheFirstReadingInsteadOfFailing() {
		ReportVerdict.Read first = firstReading(NONE);

		assertThat(ReportVerdict.settle(generated(UNREADABLE, GenerationStop.COMPLETE), ROOT, ALIASES, first)).isSameAs(first);
		assertThat(ReportVerdict.settle(generated(BOTH, GenerationStop.TRUNCATED), ROOT, ALIASES, first)).isSameAs(first);
		assertThat(ReportVerdict.settle(generated(BOTH, GenerationStop.REFUSED), ROOT, ALIASES, first)).isSameAs(first);
		assertThat(ReportVerdict.settle(new ReportOutcome.Failed(ReportFailure.OUTPUT_REFUSED, "없다"), ROOT, ALIASES,
				first))
			.isSameAs(first);
	}

	private static ReportVerdict.Read firstReading(String text) {
		ReportVerdict.Verdict verdict = ReportVerdict.judge(generated(text, GenerationStop.COMPLETE), ROOT, ALIASES);
		return ((ReportVerdict.RetryMissing) verdict).first();
	}

	private static String overview(ReportVerdict.Read read) {
		return read.output().draft().overview();
	}

	private static ReportOutcome generated(String text, GenerationStop stop) {
		return new ReportOutcome.Generated(text, new TokenUsage(10, 20), stop);
	}

}
