package net.yozmnews.event.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.event.adapter.out.fake.FakeArticleEmbedder;
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 임베더 결선 — (profile, provider) 조합마다 어떤 임베더가 등록되는지 본다. 결선 클래스는 각 패키지에서 이름으로 올린다.
 * <p>
 * 지키는 규칙: event-R7 · event-R9 · platform-R18
 */
class EmbeddingProviderWiringTests {

	private static final String OPENAI = "net.yozmnews.event.adapter.out.openai.OpenAiCompatibleEmbedderConfiguration";

	private static final String FAKE = "net.yozmnews.event.adapter.out.fake.FakeArticleEmbedderConfiguration";

	private static final String[] EXTERNAL = { "yozmnews.embedding.provider=openai-compatible",
			"yozmnews.embedding.openai-compatible.base-url=https://api.example.test/v1",
			"yozmnews.embedding.openai-compatible.model=m-1", "yozmnews.embedding.openai-compatible.dimensions=256" };

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withPropertyValues("spring.profiles.default=web,worker")
		.withUserConfiguration(load(OPENAI), load(FAKE));

	@Test
	void localDevelopmentWithTheDefaultProviderUsesTheFake() {
		this.runner.withPropertyValues("spring.profiles.active=local,web,worker").run(context -> {
			assertThat(context).hasSingleBean(ArticleEmbedder.class);
			assertThat(context.getBean(ArticleEmbedder.class)).isInstanceOf(FakeArticleEmbedder.class);
		});
	}

	@Test
	void localDevelopmentWithTheExternalProviderUsesOnlyTheExternalEmbedder() {
		this.runner.withPropertyValues("spring.profiles.active=local,web,worker").withPropertyValues(EXTERNAL).run(context -> {
			assertThat(context).hasSingleBean(ArticleEmbedder.class);
			assertThat(context.getBean(ArticleEmbedder.class).info().name()).isEqualTo("openai-compatible");
		});
	}

	@Test
	void theWorkerRoleWithTheExternalProviderUsesTheExternalEmbedder() {
		this.runner.withPropertyValues("spring.profiles.active=web,worker").withPropertyValues(EXTERNAL).run(context -> {
			assertThat(context).hasSingleBean(ArticleEmbedder.class);
			assertThat(context.getBean(ArticleEmbedder.class).info().name()).isEqualTo("openai-compatible");
		});
	}

	@Test
	void theWebRoleAloneHasNoEmbedder() {
		this.runner.withPropertyValues("spring.profiles.active=web")
			.withPropertyValues(EXTERNAL)
			.run(context -> assertThat(context).doesNotHaveBean(ArticleEmbedder.class));
	}

	@Test
	void aMistypedProviderStopsTheStartupNamingTheSetting() {
		this.runner.withPropertyValues("spring.profiles.active=web,worker", "yozmnews.embedding.provider=onxx")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasStackTraceContaining("yozmnews.embedding.provider");
			});
	}

	private static Class<?> load(String name) {
		try {
			return Class.forName(name);
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
