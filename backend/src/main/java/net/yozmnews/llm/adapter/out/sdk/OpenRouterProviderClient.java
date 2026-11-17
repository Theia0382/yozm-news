package net.yozmnews.llm.adapter.out.sdk;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.openai.core.ObjectMappers;
import com.openai.errors.OpenAIServiceException;
import net.yozmnews.llm.domain.failure.OpenRouterErrors;
import net.yozmnews.llm.domain.failure.ProviderError;
import net.yozmnews.llm.domain.provider.GenerationCall;
import net.yozmnews.llm.domain.provider.GenerationOutcome;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.llm.domain.schema.OpenRouterSchemas;
import net.yozmnews.platform.SecretValue;
import org.jspecify.annotations.Nullable;

/**
 * 키 하나의 OpenRouter 클라이언트 — docs/specs/llm.md §4 · llm-R6 · R8 · R9 · R11 ~ R14 · R21 ~ R23.
 * <p>
 * OpenRouter 는 OpenAI 꼴 채팅 API 라 생성은 {@link OpenAiCompatibleChat} 으로 보낸다. 다른 곳은 둘이다 — 확인 요청은 모델 목록 조회가
 * 아니라 키 조회({@code GET /key})다(모델 목록은 인증 없이 열려 있어 키를 가리지 못한다). 오류 본문의 {@code code} 가 숫자라 SDK 의 오류
 * {@code code} · {@code type} 을 읽지 않고 상태 코드와 {@code error.metadata.error_type} 만 읽는다. 생성 요청에는 학습에 쓰는 공급자를
 * 빼고 요청 인자를 모두 지원하는 공급자로만 보내라는 라우팅을 싣고, 앱 표시 헤더는 싣지 않는다. 확인 요청과 생성이 같은 전송을 쓰고, 닫으면
 * 둘 다 풀린다.
 */
final class OpenRouterProviderClient implements LlmProviderClient {

	/** 공급자의 기본 주소 — 운영 설정으로 바꿀 수 없다 */
	static final String BASE_URL = "https://openrouter.ai/api/v1";

	/** 생성 요청마다 싣는 라우팅 — 학습에 쓰는 공급자 제외 · 요청 인자를 모두 지원하는 공급자만 */
	static final Map<String, Object> ROUTING = Map.of("provider",
			Map.of("data_collection", "deny", "require_parameters", true));

	/** 문구의 머리 */
	private static final String NAME = "OpenRouter";

	private static final OpenAiCompatibleChat.Dialect DIALECT = new OpenAiCompatibleChat.Dialect(NAME,
			OpenRouterSchemas::common, ROUTING, thrown -> failure(thrown, NAME + " 생성 요청이 실패했다 — 상태 "));

	private final URI keyUri;

	private final SecretValue key;

	private final JdkTransport transport;

	private final OpenAiCompatibleChat chat;

	/**
	 * @param baseUrl 프로덕션은 {@link #BASE_URL} 이다 — 시험이 대체 서버의 주소를 넣는다
	 */
	OpenRouterProviderClient(String baseUrl, SecretValue key, JdkTransport transport) {
		Objects.requireNonNull(baseUrl, "baseUrl");
		this.keyUri = URI.create(baseUrl + "/key");
		this.key = Objects.requireNonNull(key, "key");
		this.transport = Objects.requireNonNull(transport, "transport");
		this.chat = new OpenAiCompatibleChat(baseUrl, key, transport);
	}

	@Override
	public ProbeResult probe() {
		JdkTransport.Response response;
		try {
			response = this.transport.send(new JdkTransport.Request("GET", this.keyUri,
					Map.of("Authorization", List.of("Bearer " + this.key.reveal())), null));
		}
		catch (TransportFailure ex) {
			return ProviderFailure.of(ex).toProbe();
		}
		if (response.status() >= 200 && response.status() <= 299) {
			return new ProbeResult.Reachable();
		}
		List<String> retryAfter = response.headers().getOrDefault("retry-after", List.of());
		return answered(response.status(), OpenRouterErrors.errorType(response.body()), retryAfter,
				NAME + " 키 확인 요청이 실패했다 — 상태 ").toProbe();
	}

	@Override
	public GenerationOutcome generate(GenerationCall call) {
		return this.chat.generate(call, DIALECT);
	}

	@Override
	public void close() {
		this.chat.close();
	}

	/** 생성 요청이 답을 받지 못한 까닭 — SDK 예외의 오류 {@code code} · {@code type} 은 읽지 않는다 */
	private static ProviderFailure failure(RuntimeException thrown, String statusPrefix) {
		Objects.requireNonNull(thrown, "thrown");
		if (thrown instanceof OpenAIServiceException ex) {
			return answered(ex.statusCode(), errorType(ex), ex.headers().values("retry-after"), statusPrefix);
		}
		return OpenAiCompatibleChat.otherFailure(thrown, NAME);
	}

	/** 공급자가 오류로 답한 실패 — 확인 요청과 생성 요청이 함께 쓴다 */
	private static ProviderFailure answered(int status, @Nullable String errorType, List<String> retryAfter,
			String statusPrefix) {
		ProviderError error = new ProviderError(status, null, errorType, !retryAfter.isEmpty());
		return new ProviderFailure(OpenRouterErrors.classify(error), statusPrefix + status,
				ProviderFailure.retryAfter(retryAfter), false);
	}

	/** SDK 가 읽어 둔 오류 본문에서 종류 이름을 읽는다 — 옮기지 못하면 null */
	private static @Nullable String errorType(OpenAIServiceException ex) {
		try {
			return OpenRouterErrors.errorType(ObjectMappers.jsonMapper().writeValueAsString(ex.body()));
		}
		catch (RuntimeException | JsonProcessingException ignored) {
			return null;
		}
	}

}
