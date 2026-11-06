package net.yozmnews.event.application;

import java.time.Duration;

import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사건 기준 · 저장 기준 설정({@code yozmnews.event-assignment}) — 기본값의 정본이다 · docs/specs/event.md §4 ·
 * event-R17.
 * <p>
 * 기본값은 측정 전 목표다. 운영자는 환경 변수({@code YOZMNEWS_EVENT_ASSIGNMENT_UPPER} 식)로 바꾸고, 범위 · 관계를 어기면 기동하지
 * 않는다({@link AssignmentCriteria}).
 *
 * @param window 시간 창
 * @param upper 위 기준
 * @param lower 아래 기준
 * @param store 저장 기준
 * @param probability 확률 기준
 */
@ConfigurationProperties("yozmnews.event-assignment")
public record EventAssignmentProperties(@DefaultValue("72h") Duration window, @DefaultValue("0.80") double upper,
		@DefaultValue("0.55") double lower, @DefaultValue("0.40") double store,
		@DefaultValue("0.50") double probability) {

	/**
	 * @throws IllegalArgumentException 범위 · 관계를 어길 때
	 */
	public AssignmentCriteria criteria() {
		return new AssignmentCriteria(this.window, this.upper, this.lower, this.store, this.probability);
	}

}
