package net.yozmnews.user.adapter.out.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import net.yozmnews.user.domain.summary.SummaryEvent;
import net.yozmnews.user.domain.summary.SummaryFailure;
import net.yozmnews.user.domain.summary.SummaryInput;
import net.yozmnews.user.domain.summary.SummaryOutcome;
import net.yozmnews.user.domain.summary.SummaryPrompt;
import net.yozmnews.user.domain.summary.SummaryRequest;
import org.junit.jupiter.api.Test;

/**
 * 실제 정리 생성기의 옮기기 — 정리 요청 → 관문 요청(프롬프트 셋 · 쓸 모델 · 추론 강도 낮음 · 글 한 벌마다 2,400 의 본문 몫), 관문 결과 → 생성기
 * 결과(키 없음 · 답 · 실패 다섯). 관문은 받은 요청을 모으고 대본대로 답하는 대역이다. 스프링 없이 돈다.
 */
class LlmStorylineSummarizerTests {

	private static final LlmModel MODEL = new LlmModel(Provider.ANTHROPIC, "claude-sonnet-5-5", 200_000, true);

	private final List<UserId> users = new ArrayList<>();

	private final List<GenerationRequest> requests = new ArrayList<>();

	@Test
	void theRequestCarriesThePromptTheModelLowEffortAndOnePartOfBodyTokensForASingleTop() {
		summarizer(request -> done(generated("{}", GenerationStop.COMPLETE))).summarize(request(List.of("T1")));

		assertThat(this.users).containsExactly(new UserId(7));
		assertThat(this.requests).containsExactly(new GenerationRequest(MODEL, "지시 구획", "{\"flows\":[]}",
				"{\"type\":\"object\"}", ReasoningEffort.LOW, 2_400));
	}

	@Test
	void severalTopsGetAPartForEachTopAndTheWhole() {
		summarizer(request -> done(generated("{}", GenerationStop.COMPLETE))).summarize(request(List.of("T1", "T2")));

		assertThat(this.requests).extracting(GenerationRequest::maxBodyTokens).containsExactly(7_200);
	}

	@Test
	void aMissingKeyIsNotAFailure() {
		assertThat(summarizer(request -> new StoredGeneration.Missing()).summarize(request(List.of("T1"))))
			.isEqualTo(new SummaryOutcome.KeyMissing());
	}

	@Test
	void anAnswerKeepsItsTextTokensAndStop() {
		for (GenerationStop stop : GenerationStop.values()) {
			assertThat(summarizer(request -> done(generated("본문 " + stop, stop))).summarize(request(List.of("T1"))))
				.as("%s", stop)
				.isEqualTo(new SummaryOutcome.Generated("본문 " + stop, 1_234, 567, stop));
		}
	}

	@Test
	void eachGatewayFailureKindBecomesItsSummaryFailure() {
		assertThat(failedAs(LlmFailureKind.INVALID_KEY)).isEqualTo(new SummaryOutcome.Failed(SummaryFailure.INVALID_KEY));
		assertThat(failedAs(LlmFailureKind.PERMISSION_DENIED))
			.isEqualTo(new SummaryOutcome.Failed(SummaryFailure.PERMISSION_DENIED));
		assertThat(failedAs(LlmFailureKind.QUOTA_EXHAUSTED))
			.isEqualTo(new SummaryOutcome.Failed(SummaryFailure.QUOTA_EXHAUSTED));
		assertThat(failedAs(LlmFailureKind.TRANSIENT))
			.isEqualTo(new SummaryOutcome.Failed(SummaryFailure.PROVIDER_UNAVAILABLE));
		assertThat(failedAs(LlmFailureKind.UNEXPECTED)).isEqualTo(new SummaryOutcome.Failed(SummaryFailure.UNEXPECTED));
	}

	@Test
	void onlyKeySideFailuresStopARun() {
		assertThat(List.of(SummaryFailure.values()).stream().filter(SummaryFailure::ofKey).toList())
			.containsExactly(SummaryFailure.INVALID_KEY, SummaryFailure.PERMISSION_DENIED, SummaryFailure.QUOTA_EXHAUSTED);
	}

	private SummaryOutcome failedAs(LlmFailureKind kind) {
		return summarizer(request -> done(new GenerationResult.Failed(kind, "관문 문구", Duration.ofSeconds(1))))
			.summarize(request(List.of("T1")));
	}

	private static SummaryRequest request(List<String> aliases) {
		SummaryInput input = SummaryInput.fresh(
				Map.of(10L, List.of(new SummaryEvent(101, 1001, Instant.parse("2026-11-20T00:00:00Z"), "제목", null))),
				ZoneId.of("Asia/Seoul"));
		return new SummaryRequest(new UserId(7), MODEL, input,
				new SummaryPrompt("지시 구획", "{\"flows\":[]}", "{\"type\":\"object\"}", aliases));
	}

	private static StoredGeneration done(GenerationResult result) {
		return new StoredGeneration.Done(result);
	}

	private static GenerationResult generated(String text, GenerationStop stop) {
		return new GenerationResult.Generated(text, 1_234, 567, Duration.ofSeconds(3), stop, true);
	}

	/** 생성 호출만 대본대로 답하는 관문 대역 — 받은 사용자 · 요청을 모은다 */
	private LlmStorylineSummarizer summarizer(Function<GenerationRequest, StoredGeneration> script) {
		return new LlmStorylineSummarizer(new LlmClientFactory() {

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
				LlmStorylineSummarizerTests.this.users.add(user);
				LlmStorylineSummarizerTests.this.requests.add(request);
				return script.apply(request);
			}

		});
	}

}
