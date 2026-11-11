package net.yozmnews.event.application;

import java.time.Duration;

import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.domain.event.EventClosing;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사건 기준 · 저장 기준 · 닫기 설정({@code yozmnews.event-assignment}) — 기본값의 정본이다 · docs/specs/event.md §4 ·
 * event-R13 · event-R17.
 * <p>
 * 사건 기준 · 저장 기준의 기본값은 기본 임베딩 모델에 맞춰 평가 세트로, 닫는 기간은 운영 기록으로 정했고, 최대 기간은 측정 전
 * 목표다(ADR-0109). 운영자는 환경 변수({@code YOZMNEWS_EVENT_ASSIGNMENT_UPPER} 식)로 바꾸고, 범위 · 관계를 어기면 기동하지
 * 않는다({@link AssignmentCriteria} · {@link EventClosing}).
 *
 * @param window 시간 창
 * @param upper 위 기준
 * @param lower 아래 기준
 * @param store 저장 기준
 * @param probability 확률 기준
 * @param closeAfter 닫는 기간 — 마지막으로 기사가 붙은 뒤 이만큼 새 기사가 없으면 닫는다
 * @param maxOpen 최대 기간 — 연 뒤 이만큼 지나면 닫는다
 */
@ConfigurationProperties("yozmnews.event-assignment")
public record EventAssignmentProperties(@DefaultValue("72h") Duration window, @DefaultValue("0.90") double upper,
		@DefaultValue("0.65") double lower, @DefaultValue("0.40") double store,
		@DefaultValue("0.40") double probability, @DefaultValue("4d") Duration closeAfter,
		@DefaultValue("14d") Duration maxOpen) {

	/**
	 * @throws IllegalArgumentException 범위 · 관계를 어길 때
	 */
	public AssignmentCriteria criteria() {
		return new AssignmentCriteria(this.window, this.upper, this.lower, this.store, this.probability);
	}

	/**
	 * @throws IllegalArgumentException 닫는 기간이 창보다 짧거나 최대 기간이 닫는 기간보다 짧거나 초 단위가 아닐 때
	 */
	public EventClosing closing() {
		return EventClosing.of(this.closeAfter, this.maxOpen, this.window);
	}

}
