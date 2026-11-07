package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import net.yozmnews.event.domain.event.EventClosing;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 사건 기준 · 저장 기준 · 닫기 설정 — 아무것도 주지 않으면 기본값(창 72시간 · 위 0.80 · 아래 0.55 · 저장 0.40 · 확률 0.50 · 닫는
 * 기간 7일 · 최대 기간 14일), 하나를 주면 그 값만 바뀌고, 범위 · 관계를 어기면 컨텍스트가 뜨지 않는다.
 * <p>
 * 지키는 규칙: event-R13 · event-R17
 */
class EventAssignmentPropertiesTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(UserConfigurations.of(AssignmentConfiguration.class));

	@Test
	void withoutPropertiesTheDefaultsApply() {
		this.runner.run(context -> assertThat(context.getBean(AssignmentCriteria.class))
			.isEqualTo(new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40, 0.50)));
		this.runner.run(context -> assertThat(context.getBean(EventClosing.class))
			.isEqualTo(new EventClosing(Duration.ofDays(7), Duration.ofDays(14))));
	}

	@Test
	void theClosingPeriodsCanBeChanged() {
		this.runner.withPropertyValues("yozmnews.event-assignment.close-after=4d", "yozmnews.event-assignment.max-open=10d")
			.run(context -> assertThat(context.getBean(EventClosing.class))
				.isEqualTo(new EventClosing(Duration.ofDays(4), Duration.ofDays(10))));
	}

	@Test
	void closingPeriodsBreakingTheRelationsStopTheContext() {
		// 닫는 기간 < 창(72시간)
		this.runner.withPropertyValues("yozmnews.event-assignment.close-after=48h")
			.run(context -> assertThat(context).hasFailed());
		// 최대 기간 < 닫는 기간
		this.runner.withPropertyValues("yozmnews.event-assignment.max-open=5d")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.event-assignment.close-after=7d500ms")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void oneGivenPropertyChangesOnlyThatValue() {
		this.runner.withPropertyValues("yozmnews.event-assignment.upper=0.85")
			.run(context -> assertThat(context.getBean(AssignmentCriteria.class))
				.isEqualTo(new AssignmentCriteria(Duration.ofHours(72), 0.85, 0.55, 0.40, 0.50)));
		this.runner.withPropertyValues("yozmnews.event-assignment.window=48h")
			.run(context -> assertThat(context.getBean(AssignmentCriteria.class))
				.isEqualTo(new AssignmentCriteria(Duration.ofHours(48), 0.80, 0.55, 0.40, 0.50)));
	}

	@Test
	void valuesBreakingTheRelationsStopTheContext() {
		this.runner.withPropertyValues("yozmnews.event-assignment.upper=0.50")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.event-assignment.store=0.60")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.event-assignment.probability=0")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.event-assignment.window=0s")
			.run(context -> assertThat(context).hasFailed());
	}

}
