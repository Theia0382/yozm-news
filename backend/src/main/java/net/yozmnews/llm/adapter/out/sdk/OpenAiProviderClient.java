package net.yozmnews.llm.adapter.out.sdk;

import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.LogLevel;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.domain.failure.OpenAiErrors;
import net.yozmnews.llm.domain.failure.ProviderError;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.SecretValue;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 키 하나의 OpenAI 클라이언트 — docs/specs/llm.md §4 · llm-R6 · R8 · R9 · R11 · R12 · R14.
 * <p>
 * SDK 클라이언트를 직접 조립한다 — 주소 · 키 · 로그 수준(꺼짐) · 재시도(0)를 명시하고, 값이 비면 환경 변수를 읽는 길(Spring AI 의 설정
 * 도우미 · SDK 의 {@code fromEnv()})을 쓰지 않는다. 동기 · 비동기 클라이언트가 같은 전송을 쓴다. 실패 문구는 고정 문구 · 상태 코드 · 예외
 * 클래스 이름뿐이다 — SDK 예외의 문구에는 공급자가 되돌려준 키 조각이 실린다.
 */
final class OpenAiProviderClient implements LlmProviderClient {

	/** 공급자의 기본 주소 — 운영 설정으로 바꿀 수 없다 */
	static final String BASE_URL = "https://api.openai.com/v1";

	private final OpenAIClient client;

	private final OpenAIClientAsync asyncClient;

	/**
	 * @param baseUrl 프로덕션은 {@link #BASE_URL} 이다 — 시험이 대체 서버의 주소를 넣는다
	 */
	OpenAiProviderClient(String baseUrl, SecretValue key, JdkTransport transport) {
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

	@Override
	public ProbeResult probe() {
		try {
			this.client.models().list();
			return new ProbeResult.Reachable();
		}
		catch (OpenAIServiceException ex) {
			ProviderError error = new ProviderError(ex.statusCode(), ex.code().orElse(null), ex.type().orElse(null),
					!ex.headers().values("retry-after").isEmpty());
			return new ProbeResult.Failed(OpenAiErrors.classify(error), "OpenAI 키 확인 요청이 실패했다 — 상태 " + ex.statusCode());
		}
		catch (TransportFailure ex) {
			return new ProbeResult.Failed(ex.failureKind(), ex.getMessage());
		}
		catch (OpenAIIoException ex) {
			return new ProbeResult.Failed(LlmFailureKind.TRANSIENT, "OpenAI 와 주고받지 못했다 — " + name(ex));
		}
		catch (OpenAIException ex) {
			return new ProbeResult.Failed(LlmFailureKind.UNEXPECTED, "OpenAI 의 응답을 읽지 못했다 — " + name(ex));
		}
	}

	/**
	 * 이 키의 Spring AI 채팅 모델 — 이 클라이언트의 SDK 클라이언트(동기 · 비동기)로 만들어 Spring AI 가 따로 만들지 않게 한다. 호출 API
	 * 가 생길 때까지 부르는 프로덕션 코드가 없다.
	 */
	ChatModel chatModel(String modelId) {
		return OpenAiChatModel.builder()
			.openAiClient(this.client)
			.openAiClientAsync(this.asyncClient)
			.options(OpenAiChatOptions.builder().model(modelId).build())
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
