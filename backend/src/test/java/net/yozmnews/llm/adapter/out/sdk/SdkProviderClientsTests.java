package net.yozmnews.llm.adapter.out.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.NoStoredKeys;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.StoredKeySource;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.platform.SecretValue;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 공급자 클라이언트 포트의 운영 구현과 그 결선 — 공급자마다 그 공급자의 클라이언트를 만들고, 관문의 공개 API 가 이 구현 위에 선다.
 * <p>
 * 지키는 규칙: llm-R1 · llm-R10 · llm-R11
 */
class SdkProviderClientsTests {

	private static final SecretValue KEY = SecretValue.of("sk-never-sent-0123456789");

	@Test
	void opensTheClientOfEachProviderWithoutSendingARequest() {
		SdkProviderClients clients = new SdkProviderClients();

		try (LlmProviderClient openAi = clients.open(Provider.OPENAI, KEY);
				LlmProviderClient anthropic = clients.open(Provider.ANTHROPIC, KEY)) {
			assertThat(openAi).isInstanceOf(OpenAiProviderClient.class);
			assertThat(anthropic).isInstanceOf(AnthropicProviderClient.class);
		}
	}

	@Test
	void theGatewayIsWiredOnTheOperationalPortInEveryProfile() {
		new ApplicationContextRunner().withUserConfiguration(SdkProviderClientsConfiguration.class, Gateway.class)
			.run(context -> {
				assertThat(context).hasSingleBean(LlmProviderClients.class);
				assertThat(context.getBean(LlmProviderClients.class)).isInstanceOf(SdkProviderClients.class);
				// 꼴이 틀린 키는 요청 없이 끝난다 — 결선만 본다
				assertThat(context.getBean(LlmClientFactory.class)
					.validate(Provider.OPENAI, SecretValue.of("not a header value")))
					.isEqualTo(new KeyValidationResult.Rejected(LlmFailureKind.INVALID_KEY, "키의 형식이 틀렸다"));
			});
	}

	/** 관문의 공개 API 구현(패키지 비공개)을 스캔으로 올린다 */
	@Configuration(proxyBeanMethods = false)
	@ComponentScan(basePackageClasses = LlmClientFactory.class, useDefaultFilters = false,
			includeFilters = @ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
					classes = LlmClientFactory.class))
	static class Gateway {

		/** 관문이 요구하는 공급 인터페이스 — 이 시험은 저장한 키의 길을 부르지 않는다 */
		@Bean
		StoredKeySource storedKeySource() {
			return new NoStoredKeys();
		}

	}

}
