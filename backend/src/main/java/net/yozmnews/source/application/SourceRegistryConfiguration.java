package net.yozmnews.source.application;

import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.profile.ProfileParser;
import net.yozmnews.source.domain.registry.ProfileResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 프로필 해석기 결선 — docs/specs/source.md §4
 */
@Configuration(proxyBeanMethods = false)
class SourceRegistryConfiguration {

	@Bean
	ProfileResolver profileResolver(PresetCatalog presetCatalog, ProfileParser profileParser) {
		return new ProfileResolver(presetCatalog, profileParser);
	}

}
