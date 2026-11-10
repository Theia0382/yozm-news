package net.yozmnews.storyline.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 잇기 기준 설정 — 아무것도 주지 않으면 기본값(창 14일 · 기준 0.65), 하나를 주면 그 값만 바뀌고, 범위를 어기면 컨텍스트가 뜨지 않는다.
 * <p>
 * 지키는 규칙: storyline-R9
 */
class LinkingPropertiesTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(UserConfigurations.of(LinkingConfiguration.class));

	@Test
	void withoutPropertiesTheDefaultsApply() {
		this.runner.run(context -> assertThat(context.getBean(LinkingCriteria.class))
			.isEqualTo(new LinkingCriteria(Duration.ofDays(14), 0.65)));
	}

	@Test
	void eachValueCanBeChanged() {
		this.runner.withPropertyValues("yozmnews.storyline-linking.window=21d", "yozmnews.storyline-linking.threshold=0.7")
			.run(context -> assertThat(context.getBean(LinkingCriteria.class))
				.isEqualTo(new LinkingCriteria(Duration.ofDays(21), 0.7)));
	}

	@Test
	void valuesOutOfRangeStopTheContext() {
		this.runner.withPropertyValues("yozmnews.storyline-linking.threshold=0").run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.storyline-linking.threshold=1.1")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.storyline-linking.window=0s").run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.storyline-linking.window=1d500ms")
			.run(context -> assertThat(context).hasFailed());
	}

}
