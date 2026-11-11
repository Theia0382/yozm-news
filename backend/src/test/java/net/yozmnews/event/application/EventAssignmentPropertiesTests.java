package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.domain.event.EventClosing;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 사건 기준 · 저장 기준 · 닫기 설정 — 아무것도 주지 않으면 기본값(창 72시간 · 위 0.90 · 아래 0.65 · 저장 0.40 · 확률 0.50 · 닫는
 * 기간 4일 · 최대 기간 14일), 하나를 주면 그 값만 바뀌고, 범위 · 관계를 어기면 컨텍스트가 뜨지 않는다.
 * <p>
 * 지키는 규칙: event-R13 · event-R17
 */
class EventAssignmentPropertiesTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(UserConfigurations.of(AssignmentConfiguration.class));

	@Test
	void withoutPropertiesTheDefaultsApply() {
		this.runner.run(context -> assertThat(context.getBean(AssignmentSettings.class).criteria())
			.isEqualTo(new AssignmentCriteria(Duration.ofHours(72), 0.90, 0.65, 0.40, 0.50)));
		this.runner.run(context -> assertThat(context.getBean(EventClosing.class))
			.isEqualTo(new EventClosing(Duration.ofDays(4), Duration.ofDays(14))));
	}

	@Test
	void theClosingPeriodsCanBeChanged() {
		this.runner.withPropertyValues("yozmnews.event-assignment.close-after=5d", "yozmnews.event-assignment.max-open=10d")
			.run(context -> assertThat(context.getBean(EventClosing.class))
				.isEqualTo(new EventClosing(Duration.ofDays(5), Duration.ofDays(10))));
	}

	@Test
	void closingPeriodsBreakingTheRelationsStopTheContext() {
		// 닫는 기간 < 창(72시간)
		this.runner.withPropertyValues("yozmnews.event-assignment.close-after=48h")
			.run(context -> assertThat(context).hasFailed());
		// 최대 기간 < 닫는 기간(4일)
		this.runner.withPropertyValues("yozmnews.event-assignment.max-open=3d")
			.run(context -> assertThat(context).hasFailed());
		// 기간으로 읽히는 값이어야 닫기 기준의 초 단위 검증까지 닿는다 — 실패 원인의 문면으로 확인한다
		this.runner.withPropertyValues("yozmnews.event-assignment.close-after=P7DT0.5S")
			.run(context -> assertThat(context).getFailure()
				.rootCause()
				.hasMessage("닫는 기간 · 최대 기간은 0 보다 큰 초 단위여야 한다: PT168H0.5S"));
	}

	@Test
	void oneGivenPropertyChangesOnlyThatValue() {
		this.runner.withPropertyValues("yozmnews.event-assignment.upper=0.85")
			.run(context -> assertThat(context.getBean(AssignmentSettings.class).criteria())
				.isEqualTo(new AssignmentCriteria(Duration.ofHours(72), 0.85, 0.65, 0.40, 0.50)));
		this.runner.withPropertyValues("yozmnews.event-assignment.window=48h")
			.run(context -> assertThat(context.getBean(AssignmentSettings.class).criteria())
				.isEqualTo(new AssignmentCriteria(Duration.ofHours(48), 0.90, 0.65, 0.40, 0.50)));
		// 확률 기준을 기본값 아래로 준다
		this.runner.withPropertyValues("yozmnews.event-assignment.probability=0.40")
			.run(context -> assertThat(context.getBean(AssignmentSettings.class).criteria())
				.isEqualTo(new AssignmentCriteria(Duration.ofHours(72), 0.90, 0.65, 0.40, 0.40)));
	}

	@Test
	void valuesBreakingTheRelationsStopTheContext() {
		// 위 < 아래(0.65)
		this.runner.withPropertyValues("yozmnews.event-assignment.upper=0.60")
			.run(context -> assertThat(context).hasFailed());
		// 저장 > 아래(0.65)
		this.runner.withPropertyValues("yozmnews.event-assignment.store=0.70")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.event-assignment.probability=0")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.event-assignment.window=0s")
			.run(context -> assertThat(context).hasFailed());
	}

}
