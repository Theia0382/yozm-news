package net.yozmnews.storyline.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 잇기 기준 설정 — 아무것도 주지 않으면 기본값(창 14일 · 아래 · 위 기준 0.86 — 띠 없음 · 확률 기준 0.50), 하나를 주면 그 값만 바뀌고,
 * 범위를 어기거나 옛 키 threshold 가 있으면 컨텍스트가 뜨지 않는다.
 * <p>
 * 지키는 규칙: storyline-R9 · storyline-R35
 */
class LinkingPropertiesTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(UserConfigurations.of(LinkingConfiguration.class));

	@Test
	void withoutPropertiesTheDefaultsApply() {
		this.runner.run(context -> assertThat(context.getBean(LinkingCriteria.class))
			.isEqualTo(new LinkingCriteria(Duration.ofDays(14), 0.86, 0.86, 0.50)));
	}

	@Test
	void eachValueCanBeChanged() {
		this.runner
			.withPropertyValues("yozmnews.storyline-linking.window=21d", "yozmnews.storyline-linking.lower=0.84",
					"yozmnews.storyline-linking.upper=0.9", "yozmnews.storyline-linking.probability=0.6")
			.run(context -> assertThat(context.getBean(LinkingCriteria.class))
				.isEqualTo(new LinkingCriteria(Duration.ofDays(21), 0.84, 0.9, 0.6)));
	}

	@Test
	void valuesOutOfRangeStopTheContext() {
		this.runner.withPropertyValues("yozmnews.storyline-linking.lower=0").run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.storyline-linking.upper=1.1").run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.storyline-linking.lower=0.9", "yozmnews.storyline-linking.upper=0.84")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.storyline-linking.probability=0")
			.run(context -> assertThat(context).hasFailed());
		this.runner.withPropertyValues("yozmnews.storyline-linking.window=0s").run(context -> assertThat(context).hasFailed());
		// 기간으로 읽히는 값이어야 기준의 초 단위 검증까지 닿는다 — 실패 원인의 문면으로 확인한다
		this.runner.withPropertyValues("yozmnews.storyline-linking.window=P1DT0.5S")
			.run(context -> assertThat(context).getFailure()
				.rootCause()
				.hasMessage("시간 창은 0 보다 큰 초 단위여야 한다: PT24H0.5S"));
	}

	@Test
	void theRetiredThresholdKeyStopsTheContextAndNamesTheNewKeys() {
		this.runner.withPropertyValues("yozmnews.storyline-linking.threshold=0.86")
			.run(context -> assertThat(context).getFailure()
				.rootCause()
				.hasMessage("yozmnews.storyline-linking.threshold 는 더 쓰지 않는다 — 위 기준 upper · 아래 기준 lower · 확률 기준 "
						+ "probability 로 정한다(YOZMNEWS_STORYLINE_LINKING_UPPER · _LOWER · _PROBABILITY): 0.86"));
	}

}
