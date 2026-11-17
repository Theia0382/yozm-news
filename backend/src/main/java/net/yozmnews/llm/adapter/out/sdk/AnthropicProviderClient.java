package net.yozmnews.llm.adapter.out.sdk;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.anthropic.backends.AnthropicBackend;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.AnthropicClientAsyncImpl;
import com.anthropic.client.AnthropicClientImpl;
import com.anthropic.core.ClientOptions;
import com.anthropic.core.LogLevel;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.ErrorType;
import com.anthropic.models.messages.OutputConfig;
import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.llm.domain.failure.AnthropicErrors;
import net.yozmnews.llm.domain.failure.ProviderError;
import net.yozmnews.llm.domain.provider.GenerationCall;
import net.yozmnews.llm.domain.provider.GenerationOutcome;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.llm.domain.provider.StopReasons;
import net.yozmnews.llm.domain.schema.AnthropicSchemas;
import net.yozmnews.platform.SecretValue;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 키 하나의 Anthropic 클라이언트 — docs/specs/llm.md §4 · llm-R6 · R8 · R9 · R11 · R12 · R14 · R21 · R22.
 * <p>
 * SDK 클라이언트를 직접 조립한다 — 주소 · 키 · 로그 수준(꺼짐) · 재시도(0)를 명시하고, 값이 비면 환경 변수를 읽는 길(Spring AI 의 설정
 * 도우미 · SDK 의 {@code fromEnv()})을 쓰지 않는다. 키는 SDK 의 백엔드가 요청에 싣는다. 동기 · 비동기 클라이언트가 같은 전송을 쓴다. 생성은
 * 그 SDK 클라이언트로 만든 Spring AI 채팅 모델로 보낸다 — Spring AI 는 텍스트 블록을 이어 첫 생성으로 만들고 추론 블록은 그 뒤의 생성으로
 * 붙인다. 실패 문구는 고정 문구 · 상태 코드 · 예외 클래스 이름뿐이다.
 */
final class AnthropicProviderClient implements LlmProviderClient {

	/** 공급자의 기본 주소 — 운영 설정으로 바꿀 수 없다 */
	static final String BASE_URL = "https://api.anthropic.com";

	private final AnthropicClient client;

	private final AnthropicClientAsync asyncClient;

	/**
	 * @param baseUrl 프로덕션은 {@link #BASE_URL} 이다 — 시험이 대체 서버의 주소를 넣는다
	 */
	AnthropicProviderClient(String baseUrl, SecretValue key, JdkTransport transport) {
		Objects.requireNonNull(baseUrl, "baseUrl");
		Objects.requireNonNull(key, "key");
		AnthropicBackend backend = AnthropicBackend.builder().baseUrl(baseUrl).apiKey(key.reveal()).build();
		AnthropicSdkHttpClient http = new AnthropicSdkHttpClient(transport, backend);
		ClientOptions.Builder options = ClientOptions.builder()
			.baseUrl(baseUrl)
			.logLevel(LogLevel.OFF)
			.maxRetries(0)
			.openTelemetry(false);
		backend.applyCredentials(http, options);
		ClientOptions built = options.httpClient(http).build();
		this.client = new AnthropicClientImpl(built);
		this.asyncClient = new AnthropicClientAsyncImpl(built);
	}

	@Override
	public ProbeResult probe() {
		try {
			this.client.models().list();
			return new ProbeResult.Reachable();
		}
		catch (AnthropicException | TransportFailure ex) {
			return failure(ex, "Anthropic 키 확인 요청이 실패했다 — 상태 ").toProbe();
		}
	}

	@Override
	public GenerationOutcome generate(GenerationCall call) {
		Objects.requireNonNull(call, "call");
		ChatResponse response;
		try {
			response = chatModel(options(call)).call(new Prompt(
					List.of(new SystemMessage(call.instructions()), new UserMessage(call.userContent()))));
		}
		catch (AnthropicException | TransportFailure ex) {
			return failure(ex, "Anthropic 생성 요청이 실패했다 — 상태 ").toGeneration();
		}
		// 내용 블록이 없는 응답 — Spring AI 가 종료 사유 · 사용량 없이 빈 응답을 낸다(llm-R22)
		if (response.getResults().isEmpty()) {
			return new GenerationOutcome.Failed(LlmFailureKind.UNEXPECTED, "Anthropic 생성 응답에 내용이 없다", null, false);
		}
		Generation first = response.getResult();
		Optional<GenerationStop> stop = StopReasons.anthropic(first.getMetadata().getFinishReason());
		if (stop.isEmpty()) {
			return new GenerationOutcome.Failed(LlmFailureKind.UNEXPECTED, "Anthropic 생성 응답의 종료 사유를 모른다", null,
					false);
		}
		Usage usage = response.getMetadata().getUsage();
		return new GenerationOutcome.Answered(Objects.requireNonNullElse(first.getOutput().getText(), ""),
				tokens(usage.getPromptTokens()), tokens(usage.getCompletionTokens()), stop.get());
	}

	private static AnthropicChatOptions options(GenerationCall call) {
		AnthropicChatOptions.Builder options = AnthropicChatOptions.builder();
		options.model(call.modelId());
		options.maxTokens(call.maxOutputTokens());
		options.effort(effort(call.effort()));
		if (call.enforcesSchema()) {
			options.outputSchema(AnthropicSchemas.supported(call.outputSchema()));
		}
		return options.build();
	}

	private static OutputConfig.Effort effort(ReasoningEffort effort) {
		return switch (effort) {
			case LOW -> OutputConfig.Effort.LOW;
			case MEDIUM -> OutputConfig.Effort.MEDIUM;
			case HIGH -> OutputConfig.Effort.HIGH;
		};
	}

	/**
	 * 이 키의 Spring AI 채팅 모델 — 이 클라이언트의 SDK 클라이언트(동기 · 비동기)로 만들어 Spring AI 가 따로 만들지 않게 한다.
	 */
	ChatModel chatModel(AnthropicChatOptions options) {
		return AnthropicChatModel.builder()
			.anthropicClient(this.client)
			.anthropicClientAsync(this.asyncClient)
			.options(options)
			.build();
	}

	@Override
	public void close() {
		this.client.close();
		this.asyncClient.close();
	}

	/** 요청이 답을 받지 못한 까닭 — 확인 요청과 생성 요청이 함께 쓴다 */
	private static ProviderFailure failure(RuntimeException thrown, String statusPrefix) {
		return switch (thrown) {
			case AnthropicServiceException ex -> {
				ProviderError error = new ProviderError(ex.statusCode(), null,
						ex.errorType().map(ErrorType::asString).orElse(null),
						!ex.headers().values("retry-after").isEmpty());
				yield new ProviderFailure(AnthropicErrors.classify(error), statusPrefix + ex.statusCode(),
						ProviderFailure.retryAfter(ex.headers().values("retry-after")), false);
			}
			case TransportFailure ex -> ProviderFailure.of(ex);
			case AnthropicIoException ex ->
				new ProviderFailure(LlmFailureKind.TRANSIENT, "Anthropic 과 주고받지 못했다 — " + name(ex), null, false);
			default -> new ProviderFailure(LlmFailureKind.UNEXPECTED, "Anthropic 의 응답을 읽지 못했다 — " + name(thrown),
					null, false);
		};
	}

	private static int tokens(Integer count) {
		return (count != null) ? count : 0;
	}

	private static String name(Throwable thrown) {
		Throwable cause = (thrown.getCause() != null) ? thrown.getCause() : thrown;
		return cause.getClass().getSimpleName();
	}

}
