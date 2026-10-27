package net.yozmnews.platform.application;

import net.yozmnews.platform.domain.EventResubmissionPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 재처리 정책을 설정 값으로 만든다 — 설정 조합이 정책의 조건을 어기면 기동이 실패한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventResubmissionProperties.class)
class EventResubmissionConfiguration {

	@Bean
	EventResubmissionPolicy eventResubmissionPolicy(EventResubmissionProperties properties) {
		return new EventResubmissionPolicy(properties.resubmissionInterval(), properties.staleAfter());
	}

}
