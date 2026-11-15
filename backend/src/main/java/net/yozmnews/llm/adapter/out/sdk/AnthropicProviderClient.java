package net.yozmnews.llm.adapter.out.sdk;

import java.util.Objects;

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
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.domain.failure.AnthropicErrors;
import net.yozmnews.llm.domain.failure.ProviderError;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.SecretValue;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 키 하나의 Anthropic 클라이언트 — docs/specs/llm.md §4 · llm-R6 · R8 · R9 · R11 · R12 · R14.
 * <p>
 * SDK 클라이언트를 직접 조립한다 — 주소 · 키 · 로그 수준(꺼짐) · 재시도(0)를 명시하고, 값이 비면 환경 변수를 읽는 길(Spring AI 의 설정
 * 도우미 · SDK 의 {@code fromEnv()})을 쓰지 않는다. 키는 SDK 의 백엔드가 요청에 싣는다. 동기 · 비동기 클라이언트가 같은 전송을 쓴다. 실패
 * 문구는 고정 문구 · 상태 코드 · 예외 클래스 이름뿐이다.
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
		catch (AnthropicServiceException ex) {
			ProviderError error = new ProviderError(ex.statusCode(), null, ex.errorType().map(ErrorType::asString).orElse(null),
					!ex.headers().values("retry-after").isEmpty());
			return new ProbeResult.Failed(AnthropicErrors.classify(error),
					"Anthropic 키 확인 요청이 실패했다 — 상태 " + ex.statusCode());
		}
		catch (TransportFailure ex) {
			return new ProbeResult.Failed(ex.failureKind(), ex.getMessage());
		}
		catch (AnthropicIoException ex) {
			return new ProbeResult.Failed(LlmFailureKind.TRANSIENT, "Anthropic 과 주고받지 못했다 — " + name(ex));
		}
		catch (AnthropicException ex) {
			return new ProbeResult.Failed(LlmFailureKind.UNEXPECTED, "Anthropic 의 응답을 읽지 못했다 — " + name(ex));
		}
	}

	/**
	 * 이 키의 Spring AI 채팅 모델 — 이 클라이언트의 SDK 클라이언트(동기 · 비동기)로 만들어 Spring AI 가 따로 만들지 않게 한다. 호출 API
	 * 가 생길 때까지 부르는 프로덕션 코드가 없다.
	 */
	ChatModel chatModel(String modelId) {
		return AnthropicChatModel.builder()
			.anthropicClient(this.client)
			.anthropicClientAsync(this.asyncClient)
			.options(AnthropicChatOptions.builder().model(modelId).build())
			.build();
	}

	@Override
	public void close() {
		this.client.close();
		this.asyncClient.close();
	}

	private static String name(Throwable thrown) {
		Throwable cause = (thrown.getCause() != null) ? thrown.getCause() : thrown;
		return cause.getClass().getSimpleName();
	}

}
