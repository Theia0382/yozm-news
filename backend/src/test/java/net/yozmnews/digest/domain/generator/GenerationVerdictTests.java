package net.yozmnews.digest.domain.generator;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.domain.generator.GenerationVerdict.Fail;
import net.yozmnews.digest.domain.generator.GenerationVerdict.Read;
import net.yozmnews.digest.domain.generator.GenerationVerdict.Retry;
import net.yozmnews.llm.GenerationStop;
import org.junit.jupiter.api.Test;

/**
 * 생성 한 번의 판정 — 끝까지 쓴 응답은 읽고, 첫 호출에서 읽지 못하면 다시 묻고 다시 물은 호출에서 읽지 못하면 실패. 잘림 · 거절은 다시 묻지
 * 않고 그 사유의 코드, 생성기 실패는 그 코드 그대로
 */
class GenerationVerdictTests {

	private static final String READABLE = """
			{"overview":"흐름","top_stories":[{"title":"t","summary":"s","refs":["A1"]}],"more_headlines":[]}""";

	private static final TokenUsage USAGE = new TokenUsage(10, 20);

	@Test
	void aCompleteReadableAnswerIsRead() {
		GenerationOutcome outcome = new GenerationOutcome.Generated(READABLE, USAGE, GenerationStop.COMPLETE);

		assertThat(GenerationVerdict.judge(outcome)).isInstanceOf(Read.class);
		assertThat(GenerationVerdict.settle(outcome)).isInstanceOf(Read.class);
	}

	@Test
	void aCompleteUnreadableAnswerIsAskedAgainOnceAndThenFails() {
		GenerationOutcome outcome = new GenerationOutcome.Generated("not json", USAGE, GenerationStop.COMPLETE);

		assertThat(GenerationVerdict.judge(outcome)).isEqualTo(new Retry("JSON 이 아니다"));
		assertThat(GenerationVerdict.settle(outcome)).isEqualTo(new Fail(DigestFailure.UNREADABLE_OUTPUT, "JSON 이 아니다"));
	}

	@Test
	void aTruncatedOrRefusedAnswerIsNeverAskedAgainEvenIfReadable() {
		for (String text : new String[] { "not json", READABLE }) {
			assertThat(GenerationVerdict.judge(new GenerationOutcome.Generated(text, USAGE, GenerationStop.TRUNCATED)))
				.isEqualTo(new Fail(DigestFailure.OUTPUT_TRUNCATED, "출력이 출력 토큰 상한에 닿아 잘렸다"));
			assertThat(GenerationVerdict.judge(new GenerationOutcome.Generated(text, USAGE, GenerationStop.REFUSED)))
				.isEqualTo(new Fail(DigestFailure.OUTPUT_REFUSED, "모델이 출력을 거절했다"));
		}
	}

	@Test
	void aGeneratorFailureKeepsItsCodeAndMessage() {
		GenerationOutcome failed = new GenerationOutcome.Failed(DigestFailure.KEY_INVALID, "OpenAI 401");

		assertThat(GenerationVerdict.judge(failed)).isEqualTo(new Fail(DigestFailure.KEY_INVALID, "OpenAI 401"));
		assertThat(GenerationVerdict.settle(failed)).isEqualTo(new Fail(DigestFailure.KEY_INVALID, "OpenAI 401"));
		// 대조 — 생성기가 낸 읽기 실패 코드도 다시 묻지 않는다(다시 묻기는 끝까지 쓴 응답을 읽지 못했을 때뿐이다)
		assertThat(GenerationVerdict.judge(new GenerationOutcome.Failed(DigestFailure.UNREADABLE_OUTPUT, "x")))
			.isInstanceOf(Fail.class);
	}

}
