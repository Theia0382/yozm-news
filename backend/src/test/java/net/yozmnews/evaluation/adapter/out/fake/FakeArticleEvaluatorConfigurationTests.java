package net.yozmnews.evaluation.adapter.out.fake;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Fake 평가기의 등록 조건 — 켜진 profile 을 바꿔 컨텍스트를 띄워 본다. 활성 · 기본 profile 은 Spring 환경이 같은 이름의 속성에서
 * 읽는다.
 * <p>
 * 지키는 규칙: platform-R18 · docs/specs/evaluation.md §7(로컬 개발도 Jev 키가 있으면 Jev 를 쓴다)
 */
class FakeArticleEvaluatorConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(FakeArticleEvaluatorConfiguration.class);

	@Test
	void registersTheFakeInLocalDevelopment() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker")
			.run(context -> {
				assertThat(context).hasSingleBean(ArticleEvaluator.class);
				assertThat(context.getBean(ArticleEvaluator.class)).isInstanceOf(FakeArticleEvaluator.class);
			});
	}

	@Test
	void doesNotRegisterTheFakeInLocalDevelopmentWithAJevKey() {
		this.runner
			.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker",
					"yozmnews.jev.api-key=tsk-local-real-key")
			.run(context -> assertThat(context).doesNotHaveBean(ArticleEvaluator.class));
	}

	@Test
	void registersTheFakeInLocalDevelopmentWithAnEmptyJevKey() {
		this.runner
			.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker",
					"yozmnews.jev.api-key=")
			.run(context -> assertThat(context.getBean(ArticleEvaluator.class)).isInstanceOf(FakeArticleEvaluator.class));
	}

	@Test
	void doesNotRegisterTheFakeWithOnlyRoleProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(ArticleEvaluator.class));
	}

	@Test
	void doesNotRegisterTheFakeWithTheDefaultProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(ArticleEvaluator.class));
	}

}
