package net.yozmnews.llm.adapter.out.sdk;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.LogLevel;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.llm.domain.provider.GenerationCall;
import net.yozmnews.llm.domain.provider.GenerationOutcome;
import net.yozmnews.llm.domain.provider.StopReasons;
import net.yozmnews.platform.SecretValue;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * OpenAI 꼴 채팅 API 의 SDK 클라이언트 쌍과 생성 한 번 — OpenAI · OpenRouter 클라이언트가 함께 쓴다 · docs/specs/llm.md §4 · llm-R11 ·
 * R12 · R14 · R21 ~ R23.
 * <p>
 * SDK 클라이언트를 직접 조립한다 — 주소 · 키 · 로그 수준(꺼짐) · 재시도(0)를 명시하고, 값이 비면 환경 변수를 읽는 길(Spring AI 의 설정
 * 도우미 · SDK 의 {@code fromEnv()})을 쓰지 않는다. 동기 · 비동기 클라이언트가 같은 전송을 쓴다. 생성은 그 SDK 클라이언트로 만든 Spring AI
 * 채팅 모델로 보내고, 공급자마다 다른 것(문구의 이름 · 스키마 맞춤 · 추가 본문 · 실패 읽기)은 {@link Dialect} 로 받는다.
 */
final class OpenAiCompatibleChat implements AutoCloseable {

	/** Spring AI 가 응답의 거절 문구를 싣는 메타데이터 키 */
	private static final String REFUSAL = "refusal";

	private final OpenAIClient client;

	private final OpenAIClientAsync asyncClient;

	/**
	 * @param baseUrl 공급자의 주소 — 프로덕션은 그 공급자 클라이언트의 코드 상수다
	 */
	OpenAiCompatibleChat(String baseUrl, SecretValue key, JdkTransport transport) {
		Objects.requireNonNull(baseUrl, "baseUrl");
		Objects.requireNonNull(key, "key");
		ClientOptions options = ClientOptions.builder()
			.httpClient(new OpenAiSdkHttpClient(transport))
			.baseUrl(baseUrl)
			.apiKey(key.reveal())
			.logLevel(LogLevel.OFF)
			.maxRetries(0)
			.build();
		this.client = new OpenAIClientImpl(options);
		this.asyncClient = new OpenAIClientAsyncImpl(options);
	}

	/** 동기 SDK 클라이언트 — 공급자의 확인 요청이 쓴다 */
	OpenAIClient client() {
		return this.client;
	}

	/**
	 * 생성 요청 하나 — 다시 보내지 않는다. 예상한 실패는 {@link GenerationOutcome.Failed} 로 낸다.
	 */
	GenerationOutcome generate(GenerationCall call, Dialect dialect) {
		Objects.requireNonNull(call, "call");
		Objects.requireNonNull(dialect, "dialect");
		ChatResponse response;
		try {
			response = chatModel(options(call, dialect)).call(new Prompt(
					List.of(new SystemMessage(call.instructions()), new UserMessage(call.userContent()))));
		}
		catch (OpenAIException | TransportFailure ex) {
			return dialect.failure().apply(ex).toGeneration();
		}
		// 내용 없는 응답 — Spring AI 가 종료 사유 · 사용량 없이 빈 응답을 낸다(llm-R22)
		if (response.getResults().isEmpty()) {
			return new GenerationOutcome.Failed(LlmFailureKind.UNEXPECTED, dialect.name() + " 생성 응답에 내용이 없다", null,
					false);
		}
		Generation first = response.getResult();
		Object refusal = first.getOutput().getMetadata().get(REFUSAL);
		Optional<GenerationStop> stop = StopReasons.openAi(first.getMetadata().getFinishReason(),
				refusal instanceof String text && !text.isEmpty());
		if (stop.isEmpty()) {
			return new GenerationOutcome.Failed(LlmFailureKind.UNEXPECTED, dialect.name() + " 생성 응답의 종료 사유를 모른다",
					null, false);
		}
		Usage usage = response.getMetadata().getUsage();
		return new GenerationOutcome.Answered(Objects.requireNonNullElse(first.getOutput().getText(), ""),
				tokens(usage.getPromptTokens()), tokens(usage.getCompletionTokens()), stop.get());
	}

	private static OpenAiChatOptions options(GenerationCall call, Dialect dialect) {
		OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
		options.model(call.modelId());
		options.maxCompletionTokens(call.maxOutputTokens());
		options.reasoningEffort(effort(call.effort()));
		if (call.enforcesSchema()) {
			options.outputSchema(dialect.schema().apply(call.outputSchema()));
		}
		if (!dialect.extraBody().isEmpty()) {
			options.extraBody(dialect.extraBody());
		}
		return options.build();
	}

	private static String effort(ReasoningEffort effort) {
		return switch (effort) {
			case LOW -> "low";
			case MEDIUM -> "medium";
			case HIGH -> "high";
		};
	}

	/**
	 * 이 키의 Spring AI 채팅 모델 — 이 클라이언트의 SDK 클라이언트(동기 · 비동기)로 만들어 Spring AI 가 따로 만들지 않게 한다.
	 */
	ChatModel chatModel(OpenAiChatOptions options) {
		return OpenAiChatModel.builder()
			.openAiClient(this.client)
			.openAiClientAsync(this.asyncClient)
			.options(options)
			.build();
	}

	@Override
	public void close() {
		this.client.close();
		this.asyncClient.close();
	}

	/**
	 * 공급자의 오류 응답이 아닌 실패 — 전송이 응답을 넘기지 못한 실패({@link TransportFailure}), SDK 의 입출력 실패, 읽지 못한 응답.
	 * 공급자마다의 실패 읽기가 오류 응답 밖의 갈래에 부른다.
	 *
	 * @param name 문구의 머리 — 공급자 이름
	 */
	static ProviderFailure otherFailure(RuntimeException thrown, String name) {
		return switch (thrown) {
			case TransportFailure ex -> ProviderFailure.of(ex);
			case OpenAIIoException ex ->
				new ProviderFailure(LlmFailureKind.TRANSIENT, name + " 와 주고받지 못했다 — " + causeName(ex), null, false);
			default -> new ProviderFailure(LlmFailureKind.UNEXPECTED, name + " 의 응답을 읽지 못했다 — " + causeName(thrown),
					null, false);
		};
	}

	private static int tokens(Integer count) {
		return (count != null) ? count : 0;
	}

	/** 실패 문구에 싣는 예외 클래스 이름 — 원인이 있으면 원인의 것 */
	private static String causeName(Throwable thrown) {
		Throwable cause = (thrown.getCause() != null) ? thrown.getCause() : thrown;
		return cause.getClass().getSimpleName();
	}

	/**
	 * 생성 한 번에서 공급자마다 다른 것.
	 *
	 * @param name 실패 문구의 머리 — 공급자 이름
	 * @param schema 스키마 강제일 때 원본 출력 스키마를 그 공급자가 받는 꼴로 맞춘다(llm-R21)
	 * @param extraBody 요청 본문 최상위에 더 싣는 칸 — 비었으면 싣지 않는다
	 * @param failure SDK 예외({@link OpenAIException}) · 전송 실패({@link TransportFailure})를 실패 값으로 옮긴다
	 */
	record Dialect(String name, UnaryOperator<String> schema, Map<String, Object> extraBody,
			Function<RuntimeException, ProviderFailure> failure) {

		Dialect {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(schema, "schema");
			extraBody = Map.copyOf(extraBody);
			Objects.requireNonNull(failure, "failure");
		}

	}

}
