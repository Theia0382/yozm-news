package net.yozmnews.report.adapter.out.llm;

import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.interests;
import static net.yozmnews.report.ReportFixtures.userStoryline;
import static net.yozmnews.report.ReportFixtures.userStorylines;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.domain.alias.ReportAliases;
import net.yozmnews.report.domain.generator.ReportOutcome;
import net.yozmnews.report.domain.generator.ReportRequest;
import net.yozmnews.report.domain.generator.TokenUsage;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.prompt.ReportPrompt;
import org.junit.jupiter.api.Test;

/**
 * 실제 생성기의 옮기기 — 리포트 요청 → 관문 요청(프롬프트 셋 · 쓸 모델 · 추론 강도 · 갈래 수로 낸 본문 토큰), 관문 결과 → 생성기 결과(키 없음 ·
 * 끝까지 · 잘림 · 거절 · 실패 다섯). 관문은 받은 요청을 모으고 대본대로 답하는 대역이다. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: report-R24
 */
class LlmReportGeneratorTests {

	/** 입력 예산(200,000)은 본문 토큰과 다른 값이다 — 관문 요청의 본문 토큰이 모델의 입력 예산에서 오지 않음을 가린다 */
	private static final LlmModel MODEL = new LlmModel(Provider.ANTHROPIC, "claude-sonnet-5-5", 200_000, true);

	private static final ReportPrompt PROMPT = new ReportPrompt("지시", "{\"period\":{}}", "{\"type\":\"object\"}");

	private final List<UserId> users = new ArrayList<>();

	private final List<GenerationRequest> requests = new ArrayList<>();

	@Test
	void theRequestCarriesThePromptTheModelTheEffortAndTheBodyTokensForItsSections() {
		LlmReportGenerator generator = generator(request -> done(generated("{}", GenerationStop.COMPLETE)));

		generator.generate(request(input(interests()), ReasoningEffort.HIGH));
		generator.generate(request(input(userStorylines(userStoryline(31, 902)), interests(31)), ReasoningEffort.LOW));

		assertThat(this.users).containsExactly(new UserId(7), new UserId(7));
		assertThat(this.requests).containsExactly(
				new GenerationRequest(MODEL, "지시", "{\"period\":{}}", "{\"type\":\"object\"}", ReasoningEffort.HIGH, 4_700),
				new GenerationRequest(MODEL, "지시", "{\"period\":{}}", "{\"type\":\"object\"}", ReasoningEffort.LOW, 8_900));
	}

	@Test
	void aMissingKeyIsNotSentAndEndsAsKeyMissing() {
		LlmReportGenerator generator = generator(request -> new StoredGeneration.Missing());

		assertThat(generator.generate(request(input(interests()), ReasoningEffort.MEDIUM)))
			.isEqualTo(new ReportOutcome.Failed(ReportFailure.KEY_MISSING, "쓸 모델 공급자의 저장한 키가 없다"));
	}

	@Test
	void anAnswerKeepsItsTextTokensAndStop() {
		for (GenerationStop stop : GenerationStop.values()) {
			LlmReportGenerator generator = generator(request -> done(generated("본문 " + stop, stop)));

			assertThat(generator.generate(request(input(interests()), ReasoningEffort.MEDIUM))).as("%s", stop)
				.isEqualTo(new ReportOutcome.Generated("본문 " + stop, new TokenUsage(1_234, 567), stop));
		}
	}

	@Test
	void eachGatewayFailureKindBecomesItsReportFailureWithTheGatewayMessage() {
		assertThat(failedAs(LlmFailureKind.INVALID_KEY)).isEqualTo(failed(ReportFailure.KEY_INVALID, LlmFailureKind.INVALID_KEY));
		assertThat(failedAs(LlmFailureKind.PERMISSION_DENIED))
			.isEqualTo(failed(ReportFailure.PERMISSION_DENIED, LlmFailureKind.PERMISSION_DENIED));
		assertThat(failedAs(LlmFailureKind.QUOTA_EXHAUSTED))
			.isEqualTo(failed(ReportFailure.QUOTA_EXHAUSTED, LlmFailureKind.QUOTA_EXHAUSTED));
		assertThat(failedAs(LlmFailureKind.TRANSIENT))
			.isEqualTo(failed(ReportFailure.PROVIDER_UNAVAILABLE, LlmFailureKind.TRANSIENT));
		assertThat(failedAs(LlmFailureKind.UNEXPECTED))
			.isEqualTo(failed(ReportFailure.PROVIDER_UNEXPECTED, LlmFailureKind.UNEXPECTED));
	}

	private ReportOutcome failedAs(LlmFailureKind kind) {
		return generator(request -> done(new GenerationResult.Failed(kind, "관문 문구 " + kind, Duration.ofSeconds(1))))
			.generate(request(input(interests()), ReasoningEffort.MEDIUM));
	}

	private static ReportOutcome failed(ReportFailure failure, LlmFailureKind kind) {
		return new ReportOutcome.Failed(failure, "관문 문구 " + kind);
	}

	private static ReportRequest request(ReportInput input, ReasoningEffort effort) {
		return new ReportRequest(new UserId(7), MODEL, effort, ReportAliases.of(input), PROMPT);
	}

	private static StoredGeneration done(GenerationResult result) {
		return new StoredGeneration.Done(result);
	}

	private static GenerationResult generated(String text, GenerationStop stop) {
		return new GenerationResult.Generated(text, 1_234, 567, Duration.ofSeconds(3), stop, true);
	}

	/** 생성 호출만 대본대로 답하는 관문 대역 — 받은 사용자 · 요청을 모은다 */
	private LlmReportGenerator generator(Function<GenerationRequest, StoredGeneration> script) {
		return new LlmReportGenerator(new LlmClientFactory() {

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
				LlmReportGeneratorTests.this.users.add(user);
				LlmReportGeneratorTests.this.requests.add(request);
				return script.apply(request);
			}

		});
	}

}
