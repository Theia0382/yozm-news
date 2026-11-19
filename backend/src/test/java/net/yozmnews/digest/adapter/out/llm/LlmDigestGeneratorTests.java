package net.yozmnews.digest.adapter.out.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.DigestInputs;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.generator.DigestRequest;
import net.yozmnews.digest.domain.generator.GenerationOutcome;
import net.yozmnews.digest.domain.generator.TokenUsage;
import net.yozmnews.digest.domain.prompt.DigestPrompt;
import net.yozmnews.llm.GenerationRequest;
import net.yozmnews.llm.GenerationResult;
import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.llm.StoredGeneration;
import net.yozmnews.llm.StoredKeyValidation;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;

/**
 * 실제 생성기의 옮기기 — 다이제스트 요청 → 관문 요청(프롬프트 셋 · 쓸 모델 · 추론 강도 · 본문 몫 32,000), 관문 결과 → 생성기 결과(키 없음 ·
 * 끝까지 · 잘림 · 거절 · 실패 다섯). 관문은 받은 요청을 모으고 대본대로 답하는 대역이다. 스프링 없이 돈다.
 */
class LlmDigestGeneratorTests {

	/** 입력 예산(200,000)은 본문 몫(32,000)과 다른 값이다 — 관문 요청의 본문 몫이 모델의 입력 예산에서 오지 않음을 가린다 */
	private static final LlmModel MODEL = new LlmModel(Provider.OPENROUTER, "anthropic/claude-sonnet-5.5", 200_000, true);

	private static final DigestPrompt PROMPT = new DigestPrompt("지시 구획", "{\"candidates\":[]}", "{\"type\":\"object\"}");

	private final List<UserId> users = new ArrayList<>();

	private final List<GenerationRequest> requests = new ArrayList<>();

	@Test
	void theRequestCarriesThePromptTheModelTheEffortAndTheBodyShare() {
		LlmDigestGenerator generator = generator(request -> done(generated("{}", GenerationStop.COMPLETE)));

		generator.generate(request(ReasoningEffort.HIGH));

		assertThat(this.users).containsExactly(new UserId(7));
		assertThat(this.requests).containsExactly(new GenerationRequest(MODEL, "지시 구획", "{\"candidates\":[]}",
				"{\"type\":\"object\"}", ReasoningEffort.HIGH, 32_000));
	}

	@Test
	void aMissingKeyIsNotSentAndEndsAsKeyMissing() {
		LlmDigestGenerator generator = generator(request -> new StoredGeneration.Missing());

		assertThat(generator.generate(request(ReasoningEffort.MEDIUM)))
			.isEqualTo(new GenerationOutcome.Failed(DigestFailure.KEY_MISSING, "쓸 모델 공급자의 저장한 키가 없다"));
	}

	@Test
	void anAnswerKeepsItsTextTokensAndStop() {
		for (GenerationStop stop : GenerationStop.values()) {
			LlmDigestGenerator generator = generator(request -> done(generated("본문 " + stop, stop)));

			assertThat(generator.generate(request(ReasoningEffort.MEDIUM))).as("%s", stop)
				.isEqualTo(new GenerationOutcome.Generated("본문 " + stop, new TokenUsage(1_234, 567), stop));
		}
	}

	@Test
	void eachGatewayFailureKindBecomesItsDigestFailureWithTheGatewayMessage() {
		assertThat(failedAs(LlmFailureKind.INVALID_KEY)).isEqualTo(failed(DigestFailure.KEY_INVALID, LlmFailureKind.INVALID_KEY));
		assertThat(failedAs(LlmFailureKind.PERMISSION_DENIED))
			.isEqualTo(failed(DigestFailure.PERMISSION_DENIED, LlmFailureKind.PERMISSION_DENIED));
		assertThat(failedAs(LlmFailureKind.QUOTA_EXHAUSTED))
			.isEqualTo(failed(DigestFailure.QUOTA_EXHAUSTED, LlmFailureKind.QUOTA_EXHAUSTED));
		assertThat(failedAs(LlmFailureKind.TRANSIENT))
			.isEqualTo(failed(DigestFailure.PROVIDER_UNAVAILABLE, LlmFailureKind.TRANSIENT));
		assertThat(failedAs(LlmFailureKind.UNEXPECTED))
			.isEqualTo(failed(DigestFailure.PROVIDER_UNEXPECTED, LlmFailureKind.UNEXPECTED));
	}

	private GenerationOutcome failedAs(LlmFailureKind kind) {
		return generator(request -> done(new GenerationResult.Failed(kind, "관문 문구 " + kind, Duration.ofSeconds(1))))
			.generate(request(ReasoningEffort.MEDIUM));
	}

	private static GenerationOutcome failed(DigestFailure failure, LlmFailureKind kind) {
		return new GenerationOutcome.Failed(failure, "관문 문구 " + kind);
	}

	private static DigestRequest request(ReasoningEffort effort) {
		return new DigestRequest(new UserId(7), MODEL, effort,
				CandidateSet.of(DigestInputs.of(List.of(new DigestCandidate(new ArticleId(1), SelectionReason.SUBSCRIBED)))),
				PROMPT);
	}

	private static StoredGeneration done(GenerationResult result) {
		return new StoredGeneration.Done(result);
	}

	private static GenerationResult generated(String text, GenerationStop stop) {
		return new GenerationResult.Generated(text, 1_234, 567, Duration.ofSeconds(3), stop, true);
	}

	/** 생성 호출만 대본대로 답하는 관문 대역 — 받은 사용자 · 요청을 모은다 */
	private LlmDigestGenerator generator(Function<GenerationRequest, StoredGeneration> script) {
		return new LlmDigestGenerator(new LlmClientFactory() {

			@Override
			public KeyValidationResult validate(Provider provider, SecretValue key) {
				throw new AssertionError("키 검증을 부르지 않는다");
			}

			@Override
			public StoredKeyValidation validateStored(UserId user, Provider provider) {
				throw new AssertionError("저장한 키 검증을 부르지 않는다");
			}

			@Override
			public StoredGeneration generateStored(UserId user, GenerationRequest request) {
				LlmDigestGeneratorTests.this.users.add(user);
				LlmDigestGeneratorTests.this.requests.add(request);
				return script.apply(request);
			}

		});
	}

}
