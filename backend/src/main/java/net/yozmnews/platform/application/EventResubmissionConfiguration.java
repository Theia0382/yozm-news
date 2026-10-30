package net.yozmnews.platform.application;

import net.yozmnews.platform.domain.EventResubmissionPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 재처리 정책의 결선 — docs/specs/platform.md §4 · platform-R3
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventResubmissionProperties.class)
class EventResubmissionConfiguration {

	@Bean
	EventResubmissionPolicy eventResubmissionPolicy(EventResubmissionProperties properties) {
		return new EventResubmissionPolicy(properties.resubmissionInterval(), properties.staleAfter());
	}

}
