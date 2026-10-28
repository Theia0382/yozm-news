package net.yozmnews.source.application;

import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.profile.ProfileParser;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 등록 · 운영 테스트의 프리셋 카탈로그 — 합성 프리셋 multi(피드 one · two) · solo(피드 all). 저장소 프리셋 대신 쓴다. 이 구성을
 * 가져오는 테스트만 쓴다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RegistryTestCatalog {

	@Bean
	@Primary
	PresetCatalog registryTestCatalog(ProfileParser profileParser) {
		return PresetCatalogConfiguration.load(new PathMatchingResourcePatternResolver(),
				"classpath*:source-presets-test/registry/*", profileParser);
	}

}
