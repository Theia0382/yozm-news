package net.yozmnews.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 사건 기준 · 저장 기준 설정 — 아무것도 주지 않으면 기본값(창 72시간 · 위 0.80 · 아래 0.55 · 저장 0.40 · 확률 0.50), 하나를 주면 그
 * 값만 바뀌고, 범위 · 관계를 어기면 컨텍스트가 뜨지 않는다.
 * <p>
 * 지키는 규칙: event-R17
 */
class EventAssignmentPropertiesTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(UserConfigurations.of(AssignmentConfiguration.class));

	@Test
	void withoutPropertiesTheDefaultsApply() {
		this.runner.run(context -> assertThat(context.getBean(AssignmentCriteria.class))
			.isEqualTo(new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40, 0.50)));
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
