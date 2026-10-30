package net.yozmnews.platform.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이벤트 발행 재처리 설정({@code yozmnews.events}) — docs/specs/platform.md §7
 */
@ConfigurationProperties("yozmnews.events")
public record EventResubmissionProperties(Duration resubmissionInterval, Duration staleAfter) {
}
