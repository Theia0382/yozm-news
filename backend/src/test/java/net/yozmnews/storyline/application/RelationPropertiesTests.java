package net.yozmnews.storyline.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import net.yozmnews.storyline.domain.relation.RelationCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 관계 기준 설정 — 아무것도 주지 않으면 기본값(확률 0.60 · 한 사안 0.20 · 사건 3 · 후보 점수 0.88 · 창 14일), 하나를 주면 그 값만 바뀌고,
 * 범위를 어기면 컨텍스트가 뜨지 않는다.
 * <p>
 * 지키는 규칙: storyline-R27 · storyline-R29
 */
class RelationPropertiesTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(UserConfigurations.of(RelationConfiguration.class));

	@Test
	void withoutPropertiesTheDefaultsApply() {
		this.runner.run(context -> assertThat(context.getBean(RelationCriteria.class))
			.isEqualTo(new RelationCriteria(0.60, 0.20, 3, 0.88, Duration.ofDays(14))));
	}

	@Test
	void eachValueCanBeChanged() {
		this.runner
			.withPropertyValues("yozmnews.storyline-relation.probability=0.7", "yozmnews.storyline-relation.one-matter=0.3",
					"yozmnews.storyline-relation.min-events=4", "yozmnews.storyline-relation.candidate-score=0.9",
					"yozmnews.storyline-relation.window=21d")
			.run(context -> assertThat(context.getBean(RelationCriteria.class))
				.isEqualTo(new RelationCriteria(0.7, 0.3, 4, 0.9, Duration.ofDays(21))));
	}

	@Test
	void valuesOutOfRangeStopTheContext() {
		this.runner.withPropertyValues("yozmnews.storyline-relation.probability=0")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.storyline-relation.one-matter=1.1")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.storyline-relation.candidate-score=1.1")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.storyline-relation.window=0s")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.storyline-relation.min-events=1")
			.run(context -> assertThat(context).getFailure().rootCause().hasMessage("사건 수 하한은 2 이상이다: 1"));
	}

}
