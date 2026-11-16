package net.yozmnews.digest.adapter.out.fake;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.digest.domain.generator.DigestGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 다이제스트 생성기 로컬 대체물의 등록 조건 — 켜진 profile 을 바꿔 컨텍스트를 띄워 본다.
 * <p>
 * 지키는 규칙: digest-R12 · platform-R18
 */
class FakeDigestGeneratorConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(FakeDigestGeneratorConfiguration.class);

	@Test
	void registersTheFakeInLocalDevelopment() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker")
			.run(context -> {
				assertThat(context).hasSingleBean(DigestGenerator.class);
				assertThat(context.getBean(DigestGenerator.class)).isInstanceOf(FakeDigestGenerator.class);
			});
	}

	@Test
	void doesNotRegisterTheFakeWithOnlyRoleProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(DigestGenerator.class));
	}

	@Test
	void doesNotRegisterTheFakeWithTheDefaultProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(DigestGenerator.class));
	}

}
