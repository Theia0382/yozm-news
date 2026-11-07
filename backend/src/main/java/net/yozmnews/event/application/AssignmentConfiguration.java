package net.yozmnews.event.application;

import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import net.yozmnews.event.domain.event.EventClosing;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 사건 기준 · 저장 기준 · 닫기 기준의 결선 — 설정을 검증된 기준으로 바꾼다. 어기면 기동하지 않는다 · docs/specs/event.md §4 · event-R13 ·
 * event-R17
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventAssignmentProperties.class)
class AssignmentConfiguration {

	@Bean
	AssignmentCriteria assignmentCriteria(EventAssignmentProperties properties) {
		return properties.criteria();
	}

	@Bean
	EventClosing eventClosing(EventAssignmentProperties properties) {
		return properties.closing();
	}

}
