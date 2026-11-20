package net.yozmnews.digest.domain.generator;

import static net.yozmnews.digest.DigestInputs.event;
import static net.yozmnews.digest.DigestInputs.item;
import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.DigestInputs;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.generator.GenerationVerdict.Fail;
import net.yozmnews.digest.domain.generator.GenerationVerdict.Read;
import net.yozmnews.digest.domain.generator.GenerationVerdict.Retry;
import net.yozmnews.digest.domain.generator.GenerationVerdict.RetryMissing;
import net.yozmnews.digest.domain.generator.GenerationVerdict.Verdict;
import net.yozmnews.digest.domain.item.ItemKind;
import net.yozmnews.llm.GenerationStop;
import org.junit.jupiter.api.Test;

/**
 * 생성 한 번의 판정 — 끝까지 쓴 응답은 읽고, 첫 호출에서 읽지 못하면 다시 묻고 다시 물은 호출에서 읽지 못하면 실패. 잘림 · 거절은 다시 묻지
 * 않고 그 사유의 코드, 생성기 실패는 그 코드 그대로
 */
class GenerationVerdictTests {

	/** 항목 I1 하나(주요 뉴스) */
	private static final CandidateSet SET = CandidateSet
		.of(DigestInputs.items(item(ItemKind.TOP_STORY, event(1, SelectionReason.SUBSCRIBED, 11))));

	private static final String READABLE = """
			{"overview":"흐름","top_stories":{"I1":{"title":"t","summary":"s"}},"headlines":{}}""";

	private static final TokenUsage USAGE = new TokenUsage(10, 20);

	@Test
	void aCompleteReadableAnswerIsRead() {
		GenerationOutcome outcome = new GenerationOutcome.Generated(READABLE, USAGE, GenerationStop.COMPLETE);

		assertThat(GenerationVerdict.judge(outcome, SET)).isInstanceOf(Read.class);
		assertThat(GenerationVerdict.settle(outcome, SET)).isInstanceOf(Read.class);
	}

	/** 항목 I1(주요 뉴스) · I2(헤드라인) */
	private static final CandidateSet TWO = CandidateSet.of(DigestInputs.items(
			item(ItemKind.TOP_STORY, event(1, SelectionReason.SUBSCRIBED, 11)),
			item(ItemKind.HEADLINE, event(2, SelectionReason.SUBSCRIBED, 12))));

	private static final String BOTH = """
			{"overview":"둘","top_stories":{"I1":{"title":"t","summary":"s"}},"headlines":{"I2":{"title":"h"}}}""";

	private static final String WITHOUT_I2 = """
			{"overview":"하나","top_stories":{"I1":{"title":"t","summary":"s"}},"headlines":{}}""";

	private static final String NEITHER = """
			{"overview":"없음","top_stories":{},"headlines":{}}""";

	@Test
	void anAnswerCoveringEveryItemIsReadWithNothingMissing() {
		assertThat(GenerationVerdict.judge(complete(BOTH), TWO)).isInstanceOfSatisfying(Read.class,
				read -> assertThat(read.firstMissing()).isZero());
	}

	@Test
	void anAnswerWithAMissingItemIsAskedAgainWithTheMissingAliasAndKeepsTheFirstRead() {
		Verdict verdict = GenerationVerdict.judge(complete(WITHOUT_I2), TWO);

		assertThat(verdict).isInstanceOfSatisfying(RetryMissing.class, retry -> {
			assertThat(retry.diagnosis()).isEqualTo("출력에 없는 항목: I2");
			assertThat(retry.first().firstMissing()).isEqualTo(1);
			assertThat(retry.first().output().draft().overview()).isEqualTo("하나");
		});
	}

	@Test
	void afterAskingForMissingItemsTheReadWithFewerMissingWinsAndTheFirstCountStays() {
		Read first = ((RetryMissing) GenerationVerdict.judge(complete(WITHOUT_I2), TWO)).first();

		// 다시 물은 출력이 모든 항목을 채움 → 그 출력, 처음 없던 수는 1 그대로
		assertThat(GenerationVerdict.settle(complete(BOTH), TWO, first)).satisfies(read -> {
			assertThat(read.output().draft().overview()).isEqualTo("둘");
			assertThat(read.firstMissing()).isEqualTo(1);
		});
		// 같은 수만큼 빠짐 → 다시 물은 쪽
		assertThat(GenerationVerdict.settle(complete(WITHOUT_I2.replace("하나", "다시")), TWO, first).output().draft()
			.overview()).isEqualTo("다시");
		// 더 많이 빠짐 · 읽지 못함 · 잘림 · 생성기 실패 → 첫 읽기
		assertThat(GenerationVerdict.settle(complete(NEITHER), TWO, first)).isSameAs(first);
		assertThat(GenerationVerdict.settle(complete("not json"), TWO, first)).isSameAs(first);
		assertThat(GenerationVerdict.settle(new GenerationOutcome.Generated(BOTH, USAGE, GenerationStop.TRUNCATED), TWO,
				first)).isSameAs(first);
		assertThat(GenerationVerdict.settle(new GenerationOutcome.Failed(DigestFailure.PROVIDER_UNAVAILABLE, "x"), TWO,
				first)).isSameAs(first);
	}

	@Test
	void afterAskingForAnUnreadableAnswerTheMissingOfTheSecondIsTheFirstMissing() {
		assertThat(GenerationVerdict.judge(complete("not json"), TWO)).isInstanceOf(Retry.class);

		assertThat(GenerationVerdict.settle(complete(WITHOUT_I2), TWO)).isInstanceOfSatisfying(Read.class,
				read -> assertThat(read.firstMissing()).isEqualTo(1));
	}

	private static GenerationOutcome complete(String text) {
		return new GenerationOutcome.Generated(text, USAGE, GenerationStop.COMPLETE);
	}

	@Test
	void aCompleteUnreadableAnswerIsAskedAgainOnceAndThenFails() {
		GenerationOutcome outcome = new GenerationOutcome.Generated("not json", USAGE, GenerationStop.COMPLETE);

		assertThat(GenerationVerdict.judge(outcome, SET)).isEqualTo(new Retry("JSON 이 아니다"));
		assertThat(GenerationVerdict.settle(outcome, SET)).isEqualTo(new Fail(DigestFailure.UNREADABLE_OUTPUT, "JSON 이 아니다"));
	}

	@Test
	void aTruncatedOrRefusedAnswerIsNeverAskedAgainEvenIfReadable() {
		for (String text : new String[] { "not json", READABLE }) {
			assertThat(GenerationVerdict.judge(new GenerationOutcome.Generated(text, USAGE, GenerationStop.TRUNCATED), SET))
				.isEqualTo(new Fail(DigestFailure.OUTPUT_TRUNCATED, "출력이 출력 토큰 상한에 닿아 잘렸다"));
			assertThat(GenerationVerdict.judge(new GenerationOutcome.Generated(text, USAGE, GenerationStop.REFUSED), SET))
				.isEqualTo(new Fail(DigestFailure.OUTPUT_REFUSED, "모델이 출력을 거절했다"));
		}
	}

	@Test
	void aGeneratorFailureKeepsItsCodeAndMessage() {
		GenerationOutcome failed = new GenerationOutcome.Failed(DigestFailure.KEY_INVALID, "OpenAI 401");

		assertThat(GenerationVerdict.judge(failed, SET)).isEqualTo(new Fail(DigestFailure.KEY_INVALID, "OpenAI 401"));
		assertThat(GenerationVerdict.settle(failed, SET)).isEqualTo(new Fail(DigestFailure.KEY_INVALID, "OpenAI 401"));
		// 대조 — 생성기가 낸 읽기 실패 코드도 다시 묻지 않는다(다시 묻기는 끝까지 쓴 응답을 읽지 못했을 때뿐이다)
		assertThat(GenerationVerdict.judge(new GenerationOutcome.Failed(DigestFailure.UNREADABLE_OUTPUT, "x"), SET))
			.isInstanceOf(Fail.class);
	}

}
