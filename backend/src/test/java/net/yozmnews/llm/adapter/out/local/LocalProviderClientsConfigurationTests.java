package net.yozmnews.llm.adapter.out.local;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.NoStoredKeys;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.StoredKeySource;
import net.yozmnews.llm.adapter.out.sdk.SdkProviderClients;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.SecretValue;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * 로컬 대체물의 등록 조건 — 켜진 profile 을 바꿔 모듈의 실제 결선(운영 구현 · 로컬 대체물 · 관문)을 띄워 본다. 로컬 개발에서만 포트의 구현이
 * 대체물이 되고, 운영 구현 빈은 어느 쪽에서나 남는다.
 * <p>
 * 지키는 규칙: platform-R18 · llm-R16
 */
class LocalProviderClientsConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(Wiring.class);

	@Test
	void theSubstituteTakesThePortInLocalDevelopment() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker")
			.run(context -> {
				LlmProviderClients port = context.getBean(LlmProviderClients.class);

				assertThat(port).isInstanceOf(LocalProviderClients.class);
				assertThat(context).hasSingleBean(SdkProviderClients.class);
				try (LlmProviderClient client = port.open(Provider.OPENAI, SecretValue.of("yozm-local-valid"))) {
					assertThat(client.probe()).isEqualTo(new ProbeResult.Reachable());
				}
			});
	}

	@Test
	void theGatewayAnswersFakeKeysThroughTheSubstituteInLocalDevelopment() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker")
			.run(context -> {
				LlmClientFactory gateway = context.getBean(LlmClientFactory.class);

				assertThat(gateway.validate(Provider.ANTHROPIC, SecretValue.of("yozm-local-valid")))
					.isEqualTo(new KeyValidationResult.Valid());
				assertThat(gateway.validate(Provider.OPENAI, SecretValue.of("yozm-local-denied")))
					.isEqualTo(new KeyValidationResult.Rejected(LlmFailureKind.PERMISSION_DENIED,
							"로컬 대체물이 정한 결과 — PERMISSION_DENIED"));
			});
	}

	@Test
	void theOperationalImplementationIsThePortWithOnlyRoleProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web,worker")
			.run(context -> {
				assertThat(context).hasSingleBean(LlmProviderClients.class);
				assertThat(context.getBean(LlmProviderClients.class)).isInstanceOf(SdkProviderClients.class);
			});
	}

	@Test
	void theOperationalImplementationIsThePortWithTheDefaultProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker").run(context -> {
			assertThat(context).hasSingleBean(LlmProviderClients.class);
			assertThat(context.getBean(LlmProviderClients.class)).isInstanceOf(SdkProviderClients.class);
		});
	}

	/** 모듈의 실제 결선 셋을 스캔으로 올린다 — 패키지 비공개 클래스라 이름으로 고른다. 이름이 바뀌면 빈이 없어 시험이 실패한다 */
	@Configuration(proxyBeanMethods = false)
	@ComponentScan(basePackageClasses = LlmClientFactory.class, useDefaultFilters = false,
			includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
					pattern = { "net\\.yozmnews\\.llm\\.adapter\\.out\\.sdk\\.SdkProviderClientsConfiguration",
							"net\\.yozmnews\\.llm\\.adapter\\.out\\.local\\.LocalProviderClientsConfiguration",
							"net\\.yozmnews\\.llm\\.application\\.LlmClientFactoryService" }))
	static class Wiring {

		/** 관문이 요구하는 공급 인터페이스 — 이 시험은 저장한 키의 길을 부르지 않는다 */
		@Bean
		StoredKeySource storedKeySource() {
			return new NoStoredKeys();
		}

	}

}
