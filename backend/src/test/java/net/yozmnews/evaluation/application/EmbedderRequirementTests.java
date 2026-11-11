package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.evaluation.adapter.out.fake.FakeArticleEmbedder;
import net.yozmnews.evaluation.domain.embedder.ArticleEmbedder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 작업 역할의 기동 검사 — 작업 역할은 임베더 빈 없이 뜨지 않고, 화면 역할만 띄운 프로세스는 임베더를 요구하지 않는다. 켜진 profile 과 임베더
 * 빈을 바꿔 컨텍스트를 띄워 본다.
 * <p>
 * 지키는 규칙: evaluation-R46
 */
class EmbedderRequirementTests {

	private static final String NO_EMBEDDER = "임베더가 없어 작업 역할을 띄울 수 없다 — yozmnews.embedding.provider 를 확인한다";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(EmbedderRequirement.class);

	@Test
	void refusesTheWorkerRoleWithoutAnEmbedder() {
		this.runner.withPropertyValues("spring.profiles.active=worker")
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessage(NO_EMBEDDER));
	}

	@Test
	void startsTheWorkerRoleWithAnEmbedder() {
		this.runner.withPropertyValues("spring.profiles.active=worker")
			.withBean(ArticleEmbedder.class, FakeArticleEmbedder::new)
			.run(context -> assertThat(context).hasNotFailed().hasSingleBean(EmbedderRequirement.class));
	}

	@Test
	void doesNotAskTheWebRoleForAnEmbedder() {
		this.runner.withPropertyValues("spring.profiles.active=web")
			.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EmbedderRequirement.class));
	}

}
