package net.yozmnews.report.domain.generator;

import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.interests;
import static net.yozmnews.report.ReportFixtures.userStoryline;
import static net.yozmnews.report.ReportFixtures.userStorylines;
import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.llm.GenerationStop;
import net.yozmnews.platform.OutputShape;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.alias.ReportAliases;
import net.yozmnews.report.domain.output.ReportCitationCheck;
import net.yozmnews.report.domain.output.ReportOutputSchema;
import org.junit.jupiter.api.Test;

/**
 * 생성 판정과 다시 묻기 — 끝까지 쓴 응답만 읽고, 읽지 못하거나 갈래가 빠지거나 글에 별칭이 남으면 한 번 다시 묻는다. 잘림 · 거절 · 생성기 실패는
 * 다시 묻지 않는다.
 * <p>
 * 지키는 규칙: report-R8
 */
class ReportVerdictTests {

	/** 갈래 둘 — 추적한 흐름(별칭 S1 · E 없음) · 관심 변화(주제 줄 하나) */
	private static final ReportAliases ALIASES = ReportAliases.of(input(userStorylines(userStoryline(31)), interests()));

	private static final OutputShape.Obj ROOT = ReportOutputSchema.root(ALIASES);

	/** 갈래 하나 — 사용자 스토리라인 관심 줄 둘(별칭 S1 · S2) */
	private static final ReportAliases FLOWS = ReportAliases.of(input(interests(71, 72)));

	private static final OutputShape.Obj FLOWS_ROOT = ReportOutputSchema.root(FLOWS);

	private static final String BOTH = """
			{"sections":{"storylines":{"title":"흐름","body":"본문","refs":[]},\
			"interests":{"title":"관심","body":"본문","refs":[]}}}""";

	private static final String INTERESTS_ONLY = """
			{"sections":{"interests":{"title":"관심 하나","body":"본문","refs":[]}}}""";

	private static final String NONE = """
			{"sections":{}}""";

	private static final String UNREADABLE = "{}";

	@Test
	void readsACompleteAnswerWithEverySection() {
		ReportVerdict.Verdict verdict = ReportVerdict.judge(generated(BOTH, GenerationStop.COMPLETE), ROOT, ALIASES);

		assertThat(verdict).isInstanceOfSatisfying(ReportVerdict.Read.class,
				read -> assertThat(interestsTitle(read)).isEqualTo("관심"));
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
			assertThat(retry.diagnosis()).isEqualTo("출력에 없는 갈래: storylines, interests");
			assertThat(retry.shortfall()).isEqualTo(new ReportVerdict.Shortfall(2, 0));
			assertThat(retry.first().output().draft().sections()).isEmpty();
		});
	}

	@Test
	void asksAgainWhenTheWritingKeepsAnAliasOfThisRequest() {
		String withAlias = flows("S1의 관심이 새로 생겼다", "GS1 · S10 · E1 은 이번 요청의 별칭이 아니다");

		ReportVerdict.Verdict verdict = ReportVerdict.judge(generated(withAlias, GenerationStop.COMPLETE), FLOWS_ROOT, FLOWS);

		assertThat(verdict).isInstanceOfSatisfying(ReportVerdict.RetryRewrite.class, retry -> {
			assertThat(retry.diagnosis()).isEqualTo("제목 · 본문에 남은 별칭(이름으로 바꿔 쓸 것): S1");
			assertThat(retry.shortfall()).isEqualTo(new ReportVerdict.Shortfall(0, 1));
		});
		// 대조 leg — 별칭이 refs 에만 있으면 그대로 읽는다
		assertThat(ReportVerdict.judge(generated(flows("관심이 새로 생겼다", "금리 흐름"), GenerationStop.COMPLETE), FLOWS_ROOT,
				FLOWS))
			.isInstanceOf(ReportVerdict.Read.class);
	}

	@Test
	void joinsTheMissingSectionsAndTheLeftAliasesInOneDiagnosis() {
		// 별칭 S1 — 흐름 31, S2 — 관심 줄 71
		ReportAliases both = ReportAliases.of(input(userStorylines(userStoryline(31)), interests(71)));
		String answer = "{\"sections\":{\"interests\":{\"title\":\"S2 가 늘었다\",\"body\":\"본문\",\"refs\":[\"S2\"]}}}";

		ReportVerdict.Verdict verdict = ReportVerdict.judge(generated(answer, GenerationStop.COMPLETE),
				ReportOutputSchema.root(both), both);

		assertThat(verdict).isInstanceOfSatisfying(ReportVerdict.RetryRewrite.class, retry -> {
			assertThat(retry.diagnosis())
				.isEqualTo("출력에 없는 갈래: storylines · 제목 · 본문에 남은 별칭(이름으로 바꿔 쓸 것): S2");
			assertThat(retry.shortfall()).isEqualTo(new ReportVerdict.Shortfall(1, 1));
		});
	}

	@Test
	void anAnswerAskedAgainForLeftAliasesKeepsTheReadingWithTheSmallerShortfall() {
		ReportVerdict.Read oneLeft = firstReading(flows("S1 이 생겼다", "본문"), FLOWS_ROOT, FLOWS);

		// 남은 별칭 없음 — 다시 물은 쪽
		assertThat(interestsTitle(ReportVerdict.settle(generated(flows("고쳐 쓴 제목", "본문"), GenerationStop.COMPLETE), FLOWS_ROOT,
				FLOWS, oneLeft)))
			.isEqualTo("고쳐 쓴 제목");
		// 남은 별칭 둘 — 첫 읽기
		assertThat(ReportVerdict.settle(generated(flows("S1 · S2", "본문"), GenerationStop.COMPLETE), FLOWS_ROOT, FLOWS, oneLeft))
			.isSameAs(oneLeft);
		// 다시 물어도 같은 수 — 다시 물은 쪽(실패로 치지 않는다)
		assertThat(interestsTitle(ReportVerdict.settle(generated(flows("여전히 S2", "본문"), GenerationStop.COMPLETE), FLOWS_ROOT,
				FLOWS, oneLeft)))
			.isEqualTo("여전히 S2");
	}

	@Test
	void aMissingSectionWeighsMoreThanLeftAliases() {
		ReportAliases both = ReportAliases.of(input(userStorylines(userStoryline(31)), interests(71)));
		OutputShape.Obj root = ReportOutputSchema.root(both);
		// 첫 읽기 — 갈래는 다 있고 흐름 제목에 별칭 S1 이 남았다(남은 별칭 1)
		String complete = "{\"sections\":{\"storylines\":{\"title\":\"S1\",\"body\":\"본문\",\"refs\":[]},"
				+ "\"interests\":{\"title\":\"관심\",\"body\":\"본문\",\"refs\":[\"S2\"]}}}";
		ReportVerdict.Read first = firstReading(complete, root, both);
		// 다시 물은 쪽 — 별칭은 없지만 갈래가 하나 빠졌다
		String missingOne = "{\"sections\":{\"interests\":{\"title\":\"관심\",\"body\":\"본문\",\"refs\":[\"S2\"]}}}";

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
			.isInstanceOfSatisfying(ReportVerdict.Read.class, read -> assertThat(interestsTitle(read)).isEqualTo("관심 하나"));
		assertThat(ReportVerdict.settle(generated(BOTH, GenerationStop.TRUNCATED), ROOT))
			.isEqualTo(new ReportVerdict.Fail(ReportFailure.OUTPUT_TRUNCATED, "출력이 출력 토큰 상한에 닿아 잘렸다"));
		assertThat(ReportVerdict.settle(new ReportOutcome.Failed(ReportFailure.OUTPUT_REFUSED, "없다"), ROOT))
			.isEqualTo(new ReportVerdict.Fail(ReportFailure.OUTPUT_REFUSED, "없다"));
	}

	@Test
	void anAnswerAskedAgainForMissingSectionsKeepsTheReadingWithFewerMissing() {
		ReportVerdict.Read first = firstReading(NONE);

		// 더 적게 빠짐 — 다시 물은 쪽
		assertThat(interestsTitle(ReportVerdict.settle(generated(INTERESTS_ONLY, GenerationStop.COMPLETE), ROOT, ALIASES, first)))
			.isEqualTo("관심 하나");
		// 같은 수 — 다시 물은 쪽
		assertThat(ReportVerdict.settle(generated(NONE, GenerationStop.COMPLETE), ROOT, ALIASES, first))
			.isInstanceOf(ReportVerdict.Read.class)
			.isNotSameAs(first);
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

	@Test
	void aReadingWithoutAnySectionLeftAfterTheCheckFailsUnreadable() {
		// 첫 읽기(갈래가 모두 빠짐)로 돌아온 읽기 — 대조 뒤 섹션이 없다
		ReportVerdict.Read none = firstReading(NONE);

		assertThat(ReportVerdict.withoutSections(ReportCitationCheck.check(none.output().draft(), ALIASES)))
			.contains(new ReportVerdict.Fail(ReportFailure.UNREADABLE_OUTPUT, "출력에 대조를 지난 갈래 섹션이 없다"));
		// 대조 leg — 갈래 하나라도 남으면 실패가 아니다
		ReportVerdict.Read one = firstReading(INTERESTS_ONLY);
		assertThat(ReportVerdict.withoutSections(ReportCitationCheck.check(one.output().draft(), ALIASES))).isEmpty();
	}

	private static ReportVerdict.Read firstReading(String text) {
		return firstReading(text, ROOT, ALIASES);
	}

	private static ReportVerdict.Read firstReading(String text, OutputShape.Obj root, ReportAliases aliases) {
		ReportVerdict.Verdict verdict = ReportVerdict.judge(generated(text, GenerationStop.COMPLETE), root, aliases);
		return ((ReportVerdict.RetryRewrite) verdict).first();
	}

	/** 사용자 스토리라인 관심 갈래 하나의 응답 — refs 에 S1 */
	private static String flows(String title, String body) {
		return "{\"sections\":{\"interests\":{\"title\":\"" + title + "\",\"body\":\"" + body + "\",\"refs\":[\"S1\"]}}}";
	}

	/** 고른 읽기의 관심 갈래 제목 — 어느 읽기인지 가린다 */
	private static String interestsTitle(ReportVerdict.Verdict verdict) {
		return ((ReportVerdict.Read) verdict).output().draft().sections().get(ReportSectionKind.INTERESTS).title();
	}

	private static ReportOutcome generated(String text, GenerationStop stop) {
		return new ReportOutcome.Generated(text, new TokenUsage(10, 20), stop);
	}

}
