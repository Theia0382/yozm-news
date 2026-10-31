package net.yozmnews.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.platform.domain.ActiveRoles;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 역할 검사의 결선 — 켜진 profile 을 바꿔 컨텍스트를 띄워 본다. 활성 · 기본 profile 은 Spring 환경이 같은 이름의 속성에서 읽는다.
 * <p>
 * 지키는 규칙: platform-R8
 */
class RoleConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(RoleConfiguration.class);

	@Test
	void takesBothRolesFromTheDefaultProfilesWhenNoneIsActive() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker")
			.run(context -> assertThat(context.getBean(ActiveRoles.class)).isEqualTo(new ActiveRoles(true, true)));
	}

	@Test
	void takesOnlyTheWebRole() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web")
			.run(context -> assertThat(context.getBean(ActiveRoles.class)).isEqualTo(new ActiveRoles(true, false)));
	}

	@Test
	void takesOnlyTheWorkerRole() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=worker")
			.run(context -> assertThat(context.getBean(ActiveRoles.class)).isEqualTo(new ActiveRoles(false, true)));
	}

	@Test
	void takesARoleNextToAnotherProfile() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,worker")
			.run(context -> assertThat(context.getBean(ActiveRoles.class)).isEqualTo(new ActiveRoles(false, true)));
	}

	@Test
	void failsWhenOnlyAnotherProfileIsActive() {
		// 다른 profile 이 켜지면 기본 profile 은 쓰이지 않는다 — 역할 없이 조용히 뜨지 않고 멈춘다
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("--spring.profiles.active=local,web,worker");
			});
	}

}
