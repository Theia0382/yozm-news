package net.yozmnews.storyline.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import net.yozmnews.storyline.domain.storyline.StateCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 상태 기간 설정 — 아무것도 주지 않으면 기본값(잠잠 7일 · 끝남 30일), 주면 그 값, 범위를 어기면 컨텍스트가 뜨지 않는다.
 * <p>
 * 지키는 규칙: storyline-R16
 */
class StorylineStatePropertiesTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(UserConfigurations.of(StorylineStateConfiguration.class));

	@Test
	void withoutPropertiesTheDefaultsApply() {
		this.runner.run(context -> assertThat(context.getBean(StateCriteria.class))
			.isEqualTo(new StateCriteria(Duration.ofDays(7), Duration.ofDays(30))));
	}

	@Test
	void eachValueCanBeChanged() {
		this.runner
			.withPropertyValues("yozmnews.storyline-state.quiet-after=3d", "yozmnews.storyline-state.ended-after=60d")
			.run(context -> assertThat(context.getBean(StateCriteria.class))
				.isEqualTo(new StateCriteria(Duration.ofDays(3), Duration.ofDays(60))));
	}

	/**
	 * 값은 모두 기간으로 읽히고 기준의 검증이 막는다 — 실패 원인의 문면으로 확인한다(읽기 실패로 통과하지 않게).
	 */
	@Test
	void valuesOutOfRangeStopTheContext() {
		this.runner
			.withPropertyValues("yozmnews.storyline-state.quiet-after=10d", "yozmnews.storyline-state.ended-after=5d")
			.run(context -> assertThat(context).getFailure()
				.rootCause()
				.hasMessage("잠잠 기간은 끝남 기간보다 짧아야 한다: PT240H · PT120H"));
		this.runner.withPropertyValues("yozmnews.storyline-state.quiet-after=0s")
			.run(context -> assertThat(context).getFailure()
				.rootCause()
				.hasMessage("잠잠 기간은 0 보다 큰 초 단위여야 한다: PT0S"));
		this.runner.withPropertyValues("yozmnews.storyline-state.ended-after=P30DT0.5S")
			.run(context -> assertThat(context).getFailure()
				.rootCause()
				.hasMessage("끝남 기간은 0 보다 큰 초 단위여야 한다: PT720H0.5S"));
	}

}
