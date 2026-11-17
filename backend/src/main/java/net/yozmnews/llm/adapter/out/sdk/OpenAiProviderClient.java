package net.yozmnews.llm.adapter.out.sdk;

import java.util.Map;
import java.util.Objects;

import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIServiceException;
import net.yozmnews.llm.domain.failure.OpenAiErrors;
import net.yozmnews.llm.domain.failure.ProviderError;
import net.yozmnews.llm.domain.provider.GenerationCall;
import net.yozmnews.llm.domain.provider.GenerationOutcome;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.llm.domain.schema.OpenAiSchemas;
import net.yozmnews.platform.SecretValue;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 키 하나의 OpenAI 클라이언트 — docs/specs/llm.md §4 · llm-R6 · R8 · R9 · R11 · R12 · R14 · R21 · R22.
 * <p>
 * SDK 클라이언트 쌍과 생성은 {@link OpenAiCompatibleChat} 이 갖고, 이 클래스는 확인 요청(모델 목록 조회)과 OpenAI 오류 읽기를 갖는다. 실패
 * 문구는 고정 문구 · 상태 코드 · 예외 클래스 이름뿐이다 — SDK 예외의 문구에는 공급자가 되돌려준 키 조각이 실린다.
 */
final class OpenAiProviderClient implements LlmProviderClient {

	/** 공급자의 기본 주소 — 운영 설정으로 바꿀 수 없다 */
	static final String BASE_URL = "https://api.openai.com/v1";

	/** 문구의 머리 */
	private static final String NAME = "OpenAI";

	private static final OpenAiCompatibleChat.Dialect DIALECT = new OpenAiCompatibleChat.Dialect(NAME,
			OpenAiSchemas::strict, Map.of(), thrown -> failure(thrown, NAME + " 생성 요청이 실패했다 — 상태 "));

	private final OpenAiCompatibleChat chat;

	/**
	 * @param baseUrl 프로덕션은 {@link #BASE_URL} 이다 — 시험이 대체 서버의 주소를 넣는다
	 */
	OpenAiProviderClient(String baseUrl, SecretValue key, JdkTransport transport) {
		this.chat = new OpenAiCompatibleChat(baseUrl, key, transport);
	}

	@Override
	public ProbeResult probe() {
		try {
			this.chat.client().models().list();
			return new ProbeResult.Reachable();
		}
		catch (OpenAIException | TransportFailure ex) {
			return failure(ex, NAME + " 키 확인 요청이 실패했다 — 상태 ").toProbe();
		}
	}

	@Override
	public GenerationOutcome generate(GenerationCall call) {
		return this.chat.generate(call, DIALECT);
	}

	/** 이 키의 Spring AI 채팅 모델 */
	ChatModel chatModel(OpenAiChatOptions options) {
		return this.chat.chatModel(options);
	}

	@Override
	public void close() {
		this.chat.close();
	}

	/** 요청이 답을 받지 못한 까닭 — 확인 요청과 생성 요청이 함께 쓴다 */
	private static ProviderFailure failure(RuntimeException thrown, String statusPrefix) {
		Objects.requireNonNull(thrown, "thrown");
		if (thrown instanceof OpenAIServiceException ex) {
			ProviderError error = new ProviderError(ex.statusCode(), ex.code().orElse(null), ex.type().orElse(null),
					!ex.headers().values("retry-after").isEmpty());
			return new ProviderFailure(OpenAiErrors.classify(error), statusPrefix + ex.statusCode(),
					ProviderFailure.retryAfter(ex.headers().values("retry-after")), false);
		}
		return OpenAiCompatibleChat.otherFailure(thrown, NAME);
	}

}
