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
 * 생성 판정과 다시 묻기 — 끝까지 쓴 응답만 읽고, 읽지 못하거나 갈래가 빠지거나 글에 별칭이 남으면 한 번 다시 묻는다. 잘림 · 거절 · 생성기 실패는
 * 다시 묻지 않는다.
 * <p>
 * 지키는 규칙: report-R8
 */
class ReportVerdictTests {

	/** 갈래 둘 — 구독 주제 보도량 · 관심 변화 */
	private static final ReportAliases ALIASES = ReportAliases.of(input(coverage(), interests()));

	private static final OutputShape.Obj ROOT = ReportOutputSchema.root(ALIASES);

	/** 갈래 하나 — 사용자 스토리라인 관심 줄 둘(별칭 S1 · S2) */
	private static final ReportAliases FLOWS = ReportAliases.of(input(interests(71, 72)));

	private static final OutputShape.Obj FLOWS_ROOT = ReportOutputSchema.root(FLOWS);

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

		assertThat(verdict).isInstanceOfSatisfying(ReportVerdict.RetryRewrite.class, retry -> {
			assertThat(retry.diagnosis()).isEqualTo("출력에 없는 갈래: coverage, interests");
			assertThat(retry.shortfall()).isEqualTo(new ReportVerdict.Shortfall(2, 0));
			assertThat(retry.first().output().draft().overview()).isEqualTo("개요 없음");
		});
	}

	@Test
	void asksAgainWhenTheWritingKeepsAnAliasOfThisRequest() {
		String withAlias = flows("S1의 관심이 새로 생겼다", "GS1 · S10 · E1 은 이번 요청의 별칭이 아니다");

		ReportVerdict.Verdict verdict = ReportVerdict.judge(generated(withAlias, GenerationStop.COMPLETE), FLOWS_ROOT, FLOWS);

		assertThat(verdict).isInstanceOfSatisfying(ReportVerdict.RetryRewrite.class, retry -> {
			assertThat(retry.diagnosis()).isEqualTo("개요 · 제목 · 본문에 남은 별칭(이름으로 바꿔 쓸 것): S1");
			assertThat(retry.shortfall()).isEqualTo(new ReportVerdict.Shortfall(0, 1));
		});
		// 대조 leg — 별칭이 refs 에만 있으면 그대로 읽는다
		assertThat(ReportVerdict.judge(generated(flows("관심이 새로 생겼다", "금리 흐름"), GenerationStop.COMPLETE), FLOWS_ROOT,
				FLOWS))
			.isInstanceOf(ReportVerdict.Read.class);
	}

	@Test
	void joinsTheMissingSectionsAndTheLeftAliasesInOneDiagnosis() {
		ReportAliases both = ReportAliases.of(input(coverage(), interests(71)));
		String answer = "{\"overview\":\"S1 이 늘었다\",\"sections\":{\"interests\":{\"title\":\"관심\",\"body\":\"본문\",\"refs\":[\"S1\"]}}}";

		ReportVerdict.Verdict verdict = ReportVerdict.judge(generated(answer, GenerationStop.COMPLETE),
				ReportOutputSchema.root(both), both);

		assertThat(verdict).isInstanceOfSatisfying(ReportVerdict.RetryRewrite.class, retry -> {
			assertThat(retry.diagnosis())
				.isEqualTo("출력에 없는 갈래: coverage · 개요 · 제목 · 본문에 남은 별칭(이름으로 바꿔 쓸 것): S1");
			assertThat(retry.shortfall()).isEqualTo(new ReportVerdict.Shortfall(1, 1));
		});
	}

	@Test
	void anAnswerAskedAgainForLeftAliasesKeepsTheReadingWithTheSmallerShortfall() {
		ReportVerdict.Read oneLeft = firstReading(flows("S1 이 생겼다", "본문"), FLOWS_ROOT, FLOWS);

		// 남은 별칭 없음 — 다시 물은 쪽
		assertThat(overview(ReportVerdict.settle(generated(flows("고쳐 쓴 개요", "본문"), GenerationStop.COMPLETE), FLOWS_ROOT,
				FLOWS, oneLeft)))
			.isEqualTo("고쳐 쓴 개요");
		// 남은 별칭 둘 — 첫 읽기
		assertThat(ReportVerdict.settle(generated(flows("S1 · S2", "본문"), GenerationStop.COMPLETE), FLOWS_ROOT, FLOWS, oneLeft))
			.isSameAs(oneLeft);
		// 다시 물어도 같은 수 — 다시 물은 쪽(실패로 치지 않는다)
		assertThat(overview(ReportVerdict.settle(generated(flows("여전히 S2", "본문"), GenerationStop.COMPLETE), FLOWS_ROOT, FLOWS,
				oneLeft)))
			.isEqualTo("여전히 S2");
	}

	@Test
	void aMissingSectionWeighsMoreThanLeftAliases() {
		ReportAliases both = ReportAliases.of(input(coverage(), interests(71)));
		OutputShape.Obj root = ReportOutputSchema.root(both);
		// 첫 읽기 — 갈래는 다 있고 개요에 별칭 S1 이 남았다(남은 별칭 1)
		String complete = "{\"overview\":\"S1\",\"sections\":{\"coverage\":{\"title\":\"보도량\",\"body\":\"본문\",\"refs\":[]},"
				+ "\"interests\":{\"title\":\"관심\",\"body\":\"본문\",\"refs\":[\"S1\"]}}}";
		ReportVerdict.Read first = firstReading(complete, root, both);
		// 다시 물은 쪽 — 별칭은 없지만 갈래가 하나 빠졌다
		String missingOne = "{\"overview\":\"개요\",\"sections\":{\"interests\":{\"title\":\"관심\",\"body\":\"본문\",\"refs\":[\"S1\"]}}}";

		assertThat(ReportVerdict.settle(generated(missingOne, GenerationStop.COMPLETE), root, both, first)).isSameAs(first);
		assertThat(new ReportVerdict.Shortfall(1, 0)).isGreaterThan(new ReportVerdict.Shortfall(0, 5));
		assertThat(new ReportVerdict.Shortfall(0, 2)).isGreaterThan(new ReportVerdict.Shortfall(0, 1));
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
		return firstReading(text, ROOT, ALIASES);
	}

	private static ReportVerdict.Read firstReading(String text, OutputShape.Obj root, ReportAliases aliases) {
		ReportVerdict.Verdict verdict = ReportVerdict.judge(generated(text, GenerationStop.COMPLETE), root, aliases);
		return ((ReportVerdict.RetryRewrite) verdict).first();
	}

	/** 사용자 스토리라인 관심 갈래 하나의 응답 — refs 에 S1 */
	private static String flows(String overview, String body) {
		return "{\"overview\":\"" + overview + "\",\"sections\":{\"interests\":{\"title\":\"관심\",\"body\":\"" + body
				+ "\",\"refs\":[\"S1\"]}}}";
	}

	private static String overview(ReportVerdict.Read read) {
		return read.output().draft().overview();
	}

	private static ReportOutcome generated(String text, GenerationStop stop) {
		return new ReportOutcome.Generated(text, new TokenUsage(10, 20), stop);
	}

}
