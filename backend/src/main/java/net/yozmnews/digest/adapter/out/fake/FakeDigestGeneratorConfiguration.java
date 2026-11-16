package net.yozmnews.digest.adapter.out.fake;

import net.yozmnews.digest.domain.generator.DigestGenerator;
import net.yozmnews.platform.LocalDevelopment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 다이제스트 생성기 로컬 대체물의 결선 — profile {@code local} 에서 언제나 등록한다 · docs/specs/digest.md §4 · digest-R12
 */
@LocalDevelopment
@Configuration(proxyBeanMethods = false)
class FakeDigestGeneratorConfiguration {

	@Bean
	DigestGenerator digestGenerator() {
		return new FakeDigestGenerator();
	}

}
