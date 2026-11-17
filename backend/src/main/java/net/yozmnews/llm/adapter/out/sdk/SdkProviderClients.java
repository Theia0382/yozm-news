package net.yozmnews.llm.adapter.out.sdk;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

import net.yozmnews.llm.Provider;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.platform.SecretValue;

/**
 * 공급자 클라이언트 포트의 운영 구현 — docs/specs/llm.md §4 · llm-R1 · R7 · R11.
 * <p>
 * 요청마다 그 키의 클라이언트를 새 전송과 함께 만든다. 전송의 마감은 부르는 쪽이 넘긴 값이다. 공급자의 주소는 코드 상수다.
 */
public final class SdkProviderClients implements LlmProviderClients {

	private final String openAiBaseUrl;

	private final String anthropicBaseUrl;

	private final Function<Duration, JdkTransport> transports;

	public SdkProviderClients() {
		this(OpenAiProviderClient.BASE_URL, AnthropicProviderClient.BASE_URL, JdkTransport::new);
	}

	/** 시험이 대체 서버의 주소와 본문 상한을 줄인 전송을 넣는다 — 함수는 넘겨받은 마감으로 전송을 만든다 */
	SdkProviderClients(String openAiBaseUrl, String anthropicBaseUrl, Function<Duration, JdkTransport> transports) {
		this.openAiBaseUrl = Objects.requireNonNull(openAiBaseUrl, "openAiBaseUrl");
		this.anthropicBaseUrl = Objects.requireNonNull(anthropicBaseUrl, "anthropicBaseUrl");
		this.transports = Objects.requireNonNull(transports, "transports");
	}

	@Override
	public LlmProviderClient open(Provider provider, SecretValue key, Duration deadline) {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(deadline, "deadline");
		return switch (provider) {
			case OPENAI -> new OpenAiProviderClient(this.openAiBaseUrl, key, this.transports.apply(deadline));
			case ANTHROPIC -> new AnthropicProviderClient(this.anthropicBaseUrl, key, this.transports.apply(deadline));
		};
	}

}
