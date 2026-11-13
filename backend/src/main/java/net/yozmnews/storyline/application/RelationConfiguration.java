package net.yozmnews.storyline.application;

import net.yozmnews.storyline.domain.relation.RelationCriteria;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 관계 기준의 결선 — 설정을 검증된 기준으로 바꾼다. 어기면 기동하지 않는다 · docs/specs/storyline.md §4 · §7
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RelationProperties.class)
class RelationConfiguration {

	@Bean
	RelationCriteria relationCriteria(RelationProperties properties) {
		return properties.criteria();
	}

}
