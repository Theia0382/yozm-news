package net.yozmnews.user.adapter.out.local;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.platform.GuardedAction;
import net.yozmnews.user.application.HumanCheck;
import net.yozmnews.user.domain.guard.ClientAddress;
import net.yozmnews.user.domain.humancheck.HumanCheckResult;
import net.yozmnews.user.domain.humancheck.HumanCheckSetting;
import net.yozmnews.user.domain.humancheck.HumanCheckToken;
import net.yozmnews.user.domain.humancheck.HumanVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 로컬 개발의 항상 통과 봇 차단 검증기 — 로컬 개발에서 Turnstile 비밀 키가 없을 때만 등록되고, 어떤 토큰에도 통과한다. 활성 · 기본 profile 은
 * Spring 환경이 같은 이름의 속성에서 읽는다.
 * <p>
 * 지키는 규칙: user-R41 · platform-R18
 */
class LocalHumanVerifierConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(LocalHumanVerifierConfiguration.class)
		.withPropertyValues("spring.profiles.default=web,worker");

	@Test
	void registersInLocalDevelopmentWithoutASecretKeyAndPassesAnyToken() {
		this.runner.withPropertyValues("spring.profiles.active=local,web,worker").run(context -> {
			assertThat(context).hasSingleBean(HumanVerifier.class);
			assertThat(context.getBean(HumanVerifier.class)
				.verify(HumanCheckToken.parse("anything").orElseThrow(), GuardedAction.SIGN_IN,
						ClientAddress.parse("192.0.2.1").orElseThrow()))
				.isEqualTo(new HumanCheckResult.Passed());
		});
	}

	@Test
	void turnstileTurnedOnInLocalDevelopmentWithoutASecretStartsAndPasses() {
		// 공식 시험 사이트 키 · 비밀 키 없음 — 봇 차단 유스케이스가 뜨고 켜졌으며, 토큰은 항상 통과 검증기가 받는다
		this.runner.withUserConfiguration(HumanCheck.class)
			.withBean(HumanCheckSetting.class, () -> new HumanCheckSetting.Turnstile("1x00000000000000000000AA"))
			.withPropertyValues("spring.profiles.active=local,web,worker")
			.run(context -> {
				assertThat(context).hasNotFailed();
				HumanCheck check = context.getBean(HumanCheck.class);
				assertThat(check.enabled()).isTrue();
				assertThat(check.verify("0.widget-token", GuardedAction.SIGN_UP, ClientAddress.parse("192.0.2.1")
					.orElseThrow())).isEqualTo(new HumanCheckResult.Passed());
			});
	}

	@Test
	void registersInLocalDevelopmentWithAnEmptySecretKey() {
		this.runner
			.withPropertyValues("spring.profiles.active=local,web,worker", "yozmnews.human-check.turnstile.secret-key=")
			.run(context -> assertThat(context).hasSingleBean(HumanVerifier.class));
	}

	@Test
	void doesNotRegisterInLocalDevelopmentWithASecretKey() {
		this.runner
			.withPropertyValues("spring.profiles.active=local,web,worker",
					"yozmnews.human-check.turnstile.secret-key=0x4AAAAAAAlocal-real-secret")
			.run(context -> assertThat(context).doesNotHaveBean(HumanVerifier.class));
	}

	@Test
	void doesNotRegisterOutsideLocalDevelopment() {
		this.runner.withPropertyValues("spring.profiles.active=web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(HumanVerifier.class));
		this.runner.run(context -> assertThat(context).doesNotHaveBean(HumanVerifier.class));
	}

}
