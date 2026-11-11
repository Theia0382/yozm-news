package net.yozmnews.evaluation.adapter.out.fake;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.evaluation.domain.embedder.ArticleEmbedder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 임베더 로컬 대체물의 등록 조건 — 켜진 profile 을 바꿔 컨텍스트를 띄워 본다. 활성 · 기본 profile 은 Spring 환경이 같은 이름의 속성에서
 * 읽는다.
 * <p>
 * 지키는 규칙: evaluation-R47 · platform-R18
 */
class FakeArticleEmbedderConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(FakeArticleEmbedderConfiguration.class);

	@Test
	void registersTheFakeInLocalDevelopment() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker")
			.run(context -> {
				assertThat(context).hasSingleBean(ArticleEmbedder.class);
				assertThat(context.getBean(ArticleEmbedder.class)).isInstanceOf(FakeArticleEmbedder.class);
			});
	}

	@Test
	void doesNotRegisterTheFakeInLocalDevelopmentWithTheExternalProvider() {
		this.runner
			.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker",
					"yozmnews.embedding.provider=openai-compatible")
			.run(context -> assertThat(context).doesNotHaveBean(ArticleEmbedder.class));
	}

	@Test
	void doesNotRegisterTheFakeWithOnlyRoleProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(ArticleEmbedder.class));
	}

	@Test
	void doesNotRegisterTheFakeWithTheDefaultProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(ArticleEmbedder.class));
	}

}
