package net.yozmnews.digest.adapter.out.llm;

import net.yozmnews.digest.domain.generator.DigestGenerator;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.platform.LocalDevelopment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 실제 생성기의 결선 — profile {@code local} 이 아닐 때만 · docs/specs/digest.md §4 · digest-R12 · R25
 * <p>
 * {@code local} 에서는 로컬 대체물({@code FakeDigestGeneratorConfiguration})이 언제나 생성기다(ADR-0187). 역할 애너테이션이 없다 — 두
 * 역할 모두에 생성기가 하나 있다. 역할의 {@code @Profile} 과 함께 달지 않으므로 profile 식이 OR 로 섞이지 않는다.
 */
@Profile("!" + LocalDevelopment.PROFILE)
@Configuration(proxyBeanMethods = false)
class LlmDigestGeneratorConfiguration {

	@Bean
	DigestGenerator digestGenerator(LlmClientFactory gateway) {
		return new LlmDigestGenerator(gateway);
	}

}
