package net.yozmnews.digest.adapter.out.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.yozmnews.digest.adapter.out.fake.FakeDigestGeneratorConfigurationAccess;
import net.yozmnews.digest.domain.generator.DigestGenerator;
import net.yozmnews.llm.LlmClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 실제 생성기의 결선 — profile {@code local} 이 아닐 때만 등록된다. 역할 profile 이 있든 없든 하나다. 로컬 대체물의 결선과 함께 두면 어느
 * profile 에서도 생성기는 하나다(대조).
 */
class LlmDigestGeneratorConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(LlmDigestGeneratorConfiguration.class)
		.withBean(LlmClientFactory.class, () -> mock(LlmClientFactory.class));

	@Test
	void registersTheRealGeneratorWithTheRoleProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web,worker")
			.run(context -> {
				assertThat(context).hasSingleBean(DigestGenerator.class);
				assertThat(context.getBean(DigestGenerator.class)).isInstanceOf(LlmDigestGenerator.class);
			});
	}

	@Test
	void registersTheRealGeneratorWithTheDefaultProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker")
			.run(context -> assertThat(context).hasSingleBean(LlmDigestGenerator.class));
	}

	@Test
	void doesNotRegisterTheRealGeneratorInLocalDevelopment() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(DigestGenerator.class));
	}

	@Test
	void withTheLocalSubstituteWiringThereIsExactlyOneGeneratorInEveryProfile() {
		ApplicationContextRunner both = this.runner.withUserConfiguration(FakeDigestGeneratorConfigurationAccess.CONFIGURATION);
		both.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker")
			.run(context -> assertThat(context.getBean(DigestGenerator.class).getClass().getSimpleName())
				.isEqualTo("FakeDigestGenerator"));
		both.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web,worker")
			.run(context -> assertThat(context.getBean(DigestGenerator.class)).isInstanceOf(LlmDigestGenerator.class));
		both.withPropertyValues("spring.profiles.default=web,worker")
			.run(context -> assertThat(context).hasSingleBean(DigestGenerator.class));
	}

}
