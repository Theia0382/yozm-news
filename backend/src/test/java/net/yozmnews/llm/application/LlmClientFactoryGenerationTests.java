package net.yozmnews.llm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import net.yozmnews.llm.GenerationRequest;
import net.yozmnews.llm.GenerationResult;
import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.llm.StoredGeneration;
import net.yozmnews.llm.StoredKey;
import net.yozmnews.llm.StoredKeySource;
import net.yozmnews.llm.domain.provider.GenerationCall;
import net.yozmnews.llm.domain.provider.GenerationOutcome;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 저장한 키의 생성 호출 — 트랜잭션 밖에서만, 저장한 키를 받아 생성 마감(180초)으로 클라이언트를 열고, 출력 상한을 본문 몫 + 강도의 여유로 정해
 * 보내고, 일시 장애만 2 · 4 · 8초(또는 공급자가 준 시간) 기다려 다시 보내고, 결과를 그 키에 알린다. 포트 · 공급 인터페이스 · 시계 · 잠은
 * 부른 것을 적는 가짜다 — 잠은 시계를 그만큼 흐르게 한다.
 * <p>
 * 지키는 규칙: llm-R5 · llm-R9 · llm-R10 · llm-R14 · llm-R19 · llm-R20 · llm-R23
 */
@ExtendWith(OutputCaptureExtension.class)
class LlmClientFactoryGenerationTests {

	private static final Instant T0 = Instant.parse("2026-11-17T07:00:00Z");

	private static final UserId USER = new UserId(7);

	private static final SecretValue KEY = SecretValue.of("sk-generation-test-0123456789");

	private static final LlmModel STRUCTURED = new LlmModel(Provider.ANTHROPIC, "claude-x", 32_000, true);

	private static final String SCHEMA = "{\"type\":\"object\"}";

	private final MovingClock clock = new MovingClock();

	private final List<Duration> slept = new ArrayList<>();

	private final RecordingClients clients = new RecordingClients(this.clock);

	private final RecordingSource source = new RecordingSource();

	private final LlmClientFactoryService service = new LlmClientFactoryService(this.clients, this.source, this.clock,
			duration -> {
				this.slept.add(duration);
				this.clock.advance(duration);
			});

	@AfterEach
	void clearTheInterruptFlag() {
		Thread.interrupted();
	}

	@Test
	void generatesWithTheStoredKeyAndReportsTheKeyWorked() {
		this.source.stored = Optional.of(new StoredKey(41, KEY));
		this.clients.answer(Duration.ofSeconds(7),
				new GenerationOutcome.Answered("{\"a\":1}", 12, 34, GenerationStop.COMPLETE));

		StoredGeneration result = this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 1_000));

		assertThat(result).isEqualTo(new StoredGeneration.Done(new GenerationResult.Generated("{\"a\":1}", 12, 34,
				Duration.ofSeconds(7), GenerationStop.COMPLETE, true)));
		assertThat(this.clients.opened).containsExactly(Provider.ANTHROPIC);
		assertThat(this.clients.keys).containsExactly(KEY);
		assertThat(this.clients.deadlines).containsExactly(Duration.ofSeconds(180));
		assertThat(this.clients.calls).containsExactly(new GenerationCall("claude-x", true, "지시표지-service",
				"사용자표지-service", SCHEMA, ReasoningEffort.LOW, 5_000));
		assertThat(this.clients.closed).isEqualTo(1);
		assertThat(this.source.calls).containsExactly("find 7 ANTHROPIC", "checked 41 " + new KeyValidationResult.Valid());
	}

	@Test
	void theOutputBudgetFollowsTheEffort() {
		this.source.stored = Optional.of(new StoredKey(41, KEY));

		this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.MEDIUM, 1_000));
		this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.HIGH, 32_000));

		assertThat(this.clients.calls).extracting(GenerationCall::maxOutputTokens).containsExactly(9_000, 48_000);
	}

	@Test
	void theSchemaIsEnforcedOnlyWhenThereIsOneAndTheModelSupportsIt() {
		this.source.stored = Optional.of(new StoredKey(41, KEY));
		LlmModel plain = new LlmModel(Provider.ANTHROPIC, "claude-plain", 32_000, false);

		assertThat(generated(request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100)).schemaEnforced()).isTrue();
		assertThat(generated(request(plain, SCHEMA, ReasoningEffort.LOW, 100)).schemaEnforced()).isFalse();
		assertThat(generated(request(STRUCTURED, null, ReasoningEffort.LOW, 100)).schemaEnforced()).isFalse();
	}

	@Test
	void resendsTransientFailuresAfterTwoThenFourSeconds() {
		this.source.stored = Optional.of(new StoredKey(41, KEY));
		this.clients.answer(Duration.ZERO, transientFailure(), transientFailure(),
				new GenerationOutcome.Answered("{}", 1, 2, GenerationStop.COMPLETE));

		StoredGeneration result = this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100));

		assertThat(this.clients.calls).hasSize(3);
		assertThat(this.slept).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
		assertThat(result).isEqualTo(new StoredGeneration.Done(
				new GenerationResult.Generated("{}", 1, 2, Duration.ofSeconds(6), GenerationStop.COMPLETE, true)));
		assertThat(this.clients.opened).hasSize(1);
		assertThat(this.clients.closed).isEqualTo(1);
	}

	@Test
	void givesUpAfterFourSendsAndReportsTheLastFailure() {
		this.source.stored = Optional.of(new StoredKey(41, KEY));
		this.clients.answer(Duration.ZERO, transientFailure());

		StoredGeneration result = this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100));

		assertThat(this.clients.calls).hasSize(4);
		assertThat(this.slept).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8));
		assertThat(result).isEqualTo(new StoredGeneration.Done(
				new GenerationResult.Failed(LlmFailureKind.TRANSIENT, "상태 503", Duration.ofSeconds(14))));
		assertThat(this.source.calls).containsExactly("find 7 ANTHROPIC",
				"checked 41 " + new KeyValidationResult.Rejected(LlmFailureKind.TRANSIENT, "상태 503"));
	}

	@Test
	void followsTheWaitTheProviderGave() {
		this.source.stored = Optional.of(new StoredKey(41, KEY));
		this.clients.answer(Duration.ZERO,
				new GenerationOutcome.Failed(LlmFailureKind.TRANSIENT, "상태 429", Duration.ofSeconds(30), false),
				new GenerationOutcome.Answered("{}", 1, 2, GenerationStop.COMPLETE));

		this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100));

		assertThat(this.slept).containsExactly(Duration.ofSeconds(30));
	}

	@Test
	void doesNotResendAfterTheDeadlinePassed() {
		this.source.stored = Optional.of(new StoredKey(41, KEY));
		this.clients.answer(Duration.ofSeconds(180),
				new GenerationOutcome.Failed(LlmFailureKind.TRANSIENT, "마감", null, true));

		StoredGeneration result = this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100));

		assertThat(this.clients.calls).hasSize(1);
		assertThat(this.slept).isEmpty();
		assertThat(result).isEqualTo(new StoredGeneration.Done(
				new GenerationResult.Failed(LlmFailureKind.TRANSIENT, "마감", Duration.ofSeconds(180))));
	}

	@Test
	void aRejectedKeyIsNotResentAndIsReported() {
		this.source.stored = Optional.of(new StoredKey(41, KEY));
		this.clients.answer(Duration.ZERO,
				new GenerationOutcome.Failed(LlmFailureKind.INVALID_KEY, "상태 401", null, false));

		StoredGeneration result = this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100));

		assertThat(this.clients.calls).hasSize(1);
		assertThat(this.slept).isEmpty();
		assertThat(result).isEqualTo(new StoredGeneration.Done(
				new GenerationResult.Failed(LlmFailureKind.INVALID_KEY, "상태 401", Duration.ZERO)));
		assertThat(this.source.calls).containsExactly("find 7 ANTHROPIC",
				"checked 41 " + new KeyValidationResult.Rejected(LlmFailureKind.INVALID_KEY, "상태 401"));
	}

	@Test
	void aMissingStoredKeyIsNotSent() {
		assertThat(this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100)))
			.isEqualTo(new StoredGeneration.Missing());
		assertThat(this.clients.opened).isEmpty();
		assertThat(this.source.calls).containsExactly("find 7 ANTHROPIC");
	}

	@Test
	void theProviderOfTheModelIsAsked() {
		this.source.stored = Optional.of(new StoredKey(41, KEY));

		this.service.generateStored(USER,
				request(new LlmModel(Provider.OPENAI, "gpt-x", 32_000, true), SCHEMA, ReasoningEffort.LOW, 100));

		assertThat(this.source.calls.get(0)).isEqualTo("find 7 OPENAI");
		assertThat(this.clients.opened).containsExactly(Provider.OPENAI);
	}

	@Test
	void refusesToGenerateInsideATransaction() {
		TransactionSynchronizationManager.setActualTransactionActive(true);
		try {
			assertThatIllegalStateException()
				.isThrownBy(() -> this.service.generateStored(USER,
						request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100)))
				.withMessage(LlmClientFactoryService.IN_TRANSACTION);
		}
		finally {
			TransactionSynchronizationManager.setActualTransactionActive(false);
		}
		assertThat(this.source.calls).isEmpty();
		assertThat(this.clients.opened).isEmpty();
	}

	@Test
	void aMalformedStoredKeyIsReportedInvalidWithoutSending() {
		this.source.stored = Optional.of(new StoredKey(41, SecretValue.of("not a header value")));

		StoredGeneration result = this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100));

		assertThat(result).isEqualTo(new StoredGeneration.Done(
				new GenerationResult.Failed(LlmFailureKind.INVALID_KEY, "키의 형식이 틀렸다", Duration.ZERO)));
		assertThat(this.clients.opened).isEmpty();
		assertThat(this.source.calls).containsExactly("find 7 ANTHROPIC",
				"checked 41 " + new KeyValidationResult.Rejected(LlmFailureKind.INVALID_KEY, "키의 형식이 틀렸다"));
	}

	@Test
	void aStoredKeyThatCannotBeOpenedIsUnexpectedAndNotReported(CapturedOutput output) {
		this.source.failOnFind = true;

		StoredGeneration result = this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100));

		assertThat(result).isEqualTo(new StoredGeneration.Done(new GenerationResult.Failed(LlmFailureKind.UNEXPECTED,
				"LLM 생성 중 예상 못한 오류 — IllegalStateException", Duration.ZERO)));
		assertThat(this.source.calls).containsExactly("find 7 ANTHROPIC");
		assertThat(output.getAll()).contains("LLM 생성 중 예상 못한 오류 — IllegalStateException");
		assertThat(output.getAll()).doesNotContain("sk-find-failure");
	}

	@Test
	void anExceptionFromThePortIsUnexpectedAndTheClientIsClosed(CapturedOutput output) {
		this.source.stored = Optional.of(new StoredKey(41, KEY));
		this.clients.raise = true;

		StoredGeneration result = this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100));

		assertThat(result).isEqualTo(new StoredGeneration.Done(new GenerationResult.Failed(LlmFailureKind.UNEXPECTED,
				"LLM 생성 중 예상 못한 오류 — IllegalArgumentException", Duration.ZERO)));
		assertThat(this.clients.closed).isEqualTo(1);
		assertThat(output.getAll()).doesNotContain(KEY.reveal(), "Incorrect API key");
	}

	@Test
	void aFailureToReportTheResultIsNotSwallowed() {
		this.source.stored = Optional.of(new StoredKey(41, KEY));
		this.source.failOnChecked = true;

		assertThatIllegalStateException()
			.isThrownBy(() -> this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100)))
			.withMessage("결과를 쓰지 못했다");
	}

	@Test
	void anInterruptedWaitEndsTheGenerationAndKeepsTheInterrupt() {
		this.source.stored = Optional.of(new StoredKey(41, KEY));
		this.clients.answer(Duration.ZERO, transientFailure());
		LlmClientFactoryService interrupted = new LlmClientFactoryService(this.clients, this.source, this.clock,
				duration -> {
					throw new InterruptedException();
				});

		StoredGeneration result = interrupted.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100));

		assertThat(result).isEqualTo(new StoredGeneration.Done(new GenerationResult.Failed(LlmFailureKind.TRANSIENT,
				"LLM 생성을 다시 보내기 전에 기다리다 중단됐다", Duration.ZERO)));
		assertThat(this.clients.calls).hasSize(1);
		assertThat(Thread.currentThread().isInterrupted()).isTrue();
	}

	@Test
	void theResultAndTheLogCarryNoKeyAndNoPrompt(CapturedOutput output) {
		this.source.stored = Optional.of(new StoredKey(41, KEY));
		this.clients.answer(Duration.ZERO, transientFailure(),
				new GenerationOutcome.Failed(LlmFailureKind.INVALID_KEY, "상태 401", null, false));
		System.out.println("출력표지-service-generation");

		StoredGeneration result = this.service.generateStored(USER, request(STRUCTURED, SCHEMA, ReasoningEffort.LOW, 100));

		assertThat(result.toString()).doesNotContain(KEY.reveal(), "지시표지-service", "사용자표지-service");
		// 대조 — 출력이 캡처된다
		assertThat(output.getAll()).contains("출력표지-service-generation");
		assertThat(output.getAll()).doesNotContain(KEY.reveal(), "지시표지-service", "사용자표지-service");
	}

	private GenerationResult.Generated generated(GenerationRequest request) {
		return (GenerationResult.Generated) ((StoredGeneration.Done) this.service.generateStored(USER, request))
			.result();
	}

	private static GenerationRequest request(LlmModel model, String schema, ReasoningEffort effort, int bodyTokens) {
		return new GenerationRequest(model, "지시표지-service", "사용자표지-service", schema, effort, bodyTokens);
	}

	private static GenerationOutcome transientFailure() {
		return new GenerationOutcome.Failed(LlmFailureKind.TRANSIENT, "상태 503", null, false);
	}

	/** 흐르게 할 때만 흐르는 시계 */
	private static final class MovingClock implements InstantSource {

		private Instant now = T0;

		void advance(Duration duration) {
			this.now = this.now.plus(duration);
		}

		@Override
		public Instant instant() {
			return this.now;
		}

	}

	/** 부른 순서와 인자를 적는 가짜 공급 인터페이스 */
	private static final class RecordingSource implements StoredKeySource {

		private final List<String> calls = new ArrayList<>();

		private Optional<StoredKey> stored = Optional.empty();

		private boolean failOnFind;

		private boolean failOnChecked;

		@Override
		public Optional<StoredKey> find(UserId user, Provider provider) {
			this.calls.add("find " + user.value() + " " + provider);
			if (this.failOnFind) {
				throw new IllegalStateException("sk-find-failure");
			}
			return this.stored;
		}

		@Override
		public void checked(long keyId, KeyValidationResult result) {
			if (this.failOnChecked) {
				throw new IllegalStateException("결과를 쓰지 못했다");
			}
			this.calls.add("checked " + keyId + " " + result);
		}

	}

	/** 연 공급자 · 키 · 마감, 받은 생성 요청, 닫은 횟수를 적고 정해 둔 결과를 차례로 내는 가짜 포트 — 답할 때 시계를 흐르게 한다 */
	private static final class RecordingClients implements LlmProviderClients {

		private final MovingClock clock;

		private final List<Provider> opened = new ArrayList<>();

		private final List<SecretValue> keys = new ArrayList<>();

		private final List<Duration> deadlines = new ArrayList<>();

		private final List<GenerationCall> calls = new ArrayList<>();

		private final Deque<GenerationOutcome> outcomes = new ArrayDeque<>();

		private Duration latency = Duration.ZERO;

		private boolean raise;

		private int closed;

		RecordingClients(MovingClock clock) {
			this.clock = clock;
		}

		/** 요청마다 이만큼 걸려 차례로 이 결과들을 낸다 — 다 쓰면 마지막 결과를 되풀이한다 */
		void answer(Duration latency, GenerationOutcome... outcomes) {
			this.latency = latency;
			this.outcomes.clear();
			this.outcomes.addAll(List.of(outcomes));
		}

		@Override
		public LlmProviderClient open(Provider provider, SecretValue key, Duration deadline) {
			this.opened.add(provider);
			this.keys.add(key);
			this.deadlines.add(deadline);
			return new LlmProviderClient() {

				@Override
				public ProbeResult probe() {
					throw new AssertionError("생성 시험은 확인을 부르지 않는다");
				}

				@Override
				public GenerationOutcome generate(GenerationCall call) {
					RecordingClients.this.calls.add(call);
					if (RecordingClients.this.raise) {
						throw new IllegalArgumentException("Incorrect API key " + KEY.reveal());
					}
					RecordingClients.this.clock.advance(RecordingClients.this.latency);
					Deque<GenerationOutcome> outcomes = RecordingClients.this.outcomes;
					if (outcomes.isEmpty()) {
						return new GenerationOutcome.Answered("{}", 0, 0, GenerationStop.COMPLETE);
					}
					return (outcomes.size() > 1) ? outcomes.poll() : outcomes.peek();
				}

				@Override
				public void close() {
					RecordingClients.this.closed++;
				}

			};
		}

	}

}
