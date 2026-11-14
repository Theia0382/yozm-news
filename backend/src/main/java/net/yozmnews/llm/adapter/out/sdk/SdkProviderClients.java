package net.yozmnews.llm.adapter.out.sdk;

import java.util.Objects;
import java.util.function.Supplier;

import net.yozmnews.llm.Provider;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.platform.SecretValue;

/**
 * 공급자 클라이언트 포트의 운영 구현 — docs/specs/llm.md §4 · llm-R1 · R11.
 * <p>
 * 요청마다 그 키의 클라이언트를 새 전송과 함께 만든다. 공급자의 주소는 코드 상수다.
 */
public final class SdkProviderClients implements LlmProviderClients {

	private final String openAiBaseUrl;

	private final String anthropicBaseUrl;

	private final Supplier<JdkTransport> transports;

	public SdkProviderClients() {
		this(OpenAiProviderClient.BASE_URL, AnthropicProviderClient.BASE_URL, JdkTransport::new);
	}

	/** 시험이 대체 서버의 주소와 마감을 줄인 전송을 넣는다 */
	SdkProviderClients(String openAiBaseUrl, String anthropicBaseUrl, Supplier<JdkTransport> transports) {
		this.openAiBaseUrl = Objects.requireNonNull(openAiBaseUrl, "openAiBaseUrl");
		this.anthropicBaseUrl = Objects.requireNonNull(anthropicBaseUrl, "anthropicBaseUrl");
		this.transports = Objects.requireNonNull(transports, "transports");
	}

	@Override
	public LlmProviderClient open(Provider provider, SecretValue key) {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(key, "key");
		return switch (provider) {
			case OPENAI -> new OpenAiProviderClient(this.openAiBaseUrl, key, this.transports.get());
			case ANTHROPIC -> new AnthropicProviderClient(this.anthropicBaseUrl, key, this.transports.get());
		};
	}

}
