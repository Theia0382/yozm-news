package net.yozmnews.llm.adapter.out.sdk;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 공급자 클라이언트 포트의 운영 구현 결선 — docs/specs/llm.md §4. 모든 profile 에 등록하고 역할 애너테이션이 없다(두 역할 공통).
 * <p>
 * 구체 타입으로 등록한다 — 로컬 개발의 대체물이 이 구현을 넘겨받아 감싼다(llm-R16).
 */
@Configuration(proxyBeanMethods = false)
class SdkProviderClientsConfiguration {

	@Bean
	SdkProviderClients sdkProviderClients() {
		return new SdkProviderClients();
	}

}
