package net.yozmnews.source.application;

import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.profile.ProfileParser;
import net.yozmnews.source.domain.registry.ProfileResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 소스의 프로필을 얻는 해석기를 카탈로그와 검증기로 만든다. */
@Configuration(proxyBeanMethods = false)
class SourceRegistryConfiguration {

	@Bean
	ProfileResolver profileResolver(PresetCatalog presetCatalog, ProfileParser profileParser) {
		return new ProfileResolver(presetCatalog, profileParser);
	}

}
