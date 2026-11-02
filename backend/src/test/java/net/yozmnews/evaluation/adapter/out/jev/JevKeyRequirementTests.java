package net.yozmnews.evaluation.adapter.out.jev;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 작업 역할의 기동 검사 — 로컬 개발이 아닌 작업 역할은 Jev 키 없이 뜨지 않고, 화면 역할만 띄운 프로세스는 키를 요구하지 않는다. 켜진
 * profile 을 바꿔 컨텍스트를 띄워 본다.
 * <p>
 * 지키는 규칙: evaluation-R20 · ADR-0061
 */
class JevKeyRequirementTests {

	private static final String KEY_MISSING = "Jev API 키가 없어 작업 역할을 띄울 수 없다 — yozmnews.jev.api-key(환경 변수 "
			+ "YOZMNEWS_JEV_API_KEY)를 설정한다";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withPropertyValues("spring.profiles.default=web,worker")
		.withUserConfiguration(JevKeyRequirement.class);

	@Test
	void refusesTheWorkerRoleWithoutAKey() {
		this.runner.withPropertyValues("spring.profiles.active=web,worker")
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessage(KEY_MISSING));
	}

	@Test
	void refusesTheDefaultProfilesWithoutAKey() {
		this.runner.run(context -> assertThat(context).hasFailed().getFailure().rootCause().hasMessage(KEY_MISSING));
	}

	@Test
	void refusesAnEmptyKey() {
		this.runner.withPropertyValues("spring.profiles.active=worker", "yozmnews.jev.api-key=")
			.run(context -> assertThat(context).hasFailed().getFailure().rootCause().hasMessage(KEY_MISSING));
	}

	@Test
	void startsTheWorkerRoleWithAKey() {
		this.runner.withPropertyValues("spring.profiles.active=worker", "yozmnews.jev.api-key=tsk-present")
			.run(context -> assertThat(context).hasNotFailed().hasSingleBean(JevKeyRequirement.class));
	}

	@Test
	void startsTheWorkerRoleInLocalDevelopmentWithoutAKey() {
		this.runner.withPropertyValues("spring.profiles.active=local,worker")
			.run(context -> assertThat(context).hasNotFailed().hasSingleBean(JevKeyRequirement.class));
	}

	@Test
	void doesNotAskTheWebRoleForAKey() {
		this.runner.withPropertyValues("spring.profiles.active=web")
			.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(JevKeyRequirement.class));
	}

}
