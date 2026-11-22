package net.yozmnews.user.adapter.out.llm;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.llm.GenerationRequest;
import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.StoredGeneration;
import net.yozmnews.llm.StoredKeyValidation;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.summary.StorylineSummarizer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 실제 정리 생성기의 결선 — profile {@code local} 이 아닐 때만 등록된다. 역할 profile 이 있든 없든 하나다.
 */
class LlmStorylineSummarizerConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(LlmStorylineSummarizerConfiguration.class)
		.withBean(LlmClientFactory.class, UnusedGateway::new);

	@Test
	void registersTheRealSummarizerWithTheRoleProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web,worker")
			.run(context -> assertThat(context.getBean(StorylineSummarizer.class))
				.isInstanceOf(LlmStorylineSummarizer.class));
	}

	@Test
	void registersTheRealSummarizerWithTheDefaultProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker")
			.run(context -> assertThat(context).hasSingleBean(LlmStorylineSummarizer.class));
	}

	@Test
	void doesNotRegisterTheRealSummarizerInLocalDevelopment() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(StorylineSummarizer.class));
	}

	/** 결선만 보는 시험이라 불리지 않는 관문 */
	private static final class UnusedGateway implements LlmClientFactory {

		@Override
		public KeyValidationResult validate(Provider provider, SecretValue key) {
			throw new AssertionError("관문을 부르지 않는다");
		}

		@Override
		public StoredKeyValidation validateStored(UserId user, Provider provider) {
			throw new AssertionError("관문을 부르지 않는다");
		}

		@Override
		public StoredGeneration generateStored(UserId user, GenerationRequest request) {
			throw new AssertionError("관문을 부르지 않는다");
		}

	}

}
