package net.yozmnews.platform.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이벤트 발행 재처리 설정({@code yozmnews.events}). 둘 다 필수다.
 * @param resubmissionInterval 실패한 발행을 다시 처리하는 주기 — 반복 작업도 같은 키를 읽는다
 * @param staleAfter 발행하고 이만큼 지나도 끝나지 않은 발행을 멈춘 것으로 보는 시간 — Modulith staleness 감시도 같은
 * 키를 읽는다
 */
@ConfigurationProperties("yozmnews.events")
public record EventResubmissionProperties(Duration resubmissionInterval, Duration staleAfter) {
}
