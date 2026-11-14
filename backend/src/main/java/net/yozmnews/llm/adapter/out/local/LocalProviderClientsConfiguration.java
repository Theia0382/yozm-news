package net.yozmnews.llm.adapter.out.local;

import net.yozmnews.llm.adapter.out.sdk.SdkProviderClients;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.platform.LocalDevelopment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 로컬 대체물의 결선 — 로컬 개발에서 공급자 클라이언트 포트의 구현이 대체물이 된다 · docs/specs/llm.md llm-R16 · platform-R18.
 * <p>
 * 운영 구현 빈은 그대로 두고 그것을 넘겨받아 감싼다 — 가짜 키가 아닌 키는 로컬 개발에서도 실제 공급자로 간다.
 */
@LocalDevelopment
@Configuration(proxyBeanMethods = false)
class LocalProviderClientsConfiguration {

	@Bean
	@Primary
	LlmProviderClients localProviderClients(SdkProviderClients operational) {
		return new LocalProviderClients(operational);
	}

}
