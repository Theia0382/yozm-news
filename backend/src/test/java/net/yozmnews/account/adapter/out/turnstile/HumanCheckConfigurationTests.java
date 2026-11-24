package net.yozmnews.account.adapter.out.turnstile;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.account.application.HumanCheck;
import net.yozmnews.account.domain.humancheck.HumanCheckSetting;
import net.yozmnews.account.domain.humancheck.HumanVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 봇 차단 결선 — 설정 판정 빈, 비밀 키가 있을 때의 Turnstile 검증기(profile 무관), 켰는데 검증기가 없을 때의 기동 실패. 로컬 대체물 결선은
 * {@code LocalHumanVerifierConfigurationTests} 가 본다. 활성 · 기본 profile 은 Spring 환경이 같은 이름의 속성에서 읽는다.
 * <p>
 * 지키는 규칙: account-R33 · platform-R18
 */
class HumanCheckConfigurationTests {

	private static final String SECRET = "0x4AAAAAAAconfiguration-only-secret";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(HumanCheckConfiguration.class, HumanCheck.class)
		.withPropertyValues("spring.profiles.default=web,worker");

	@Test
	void offByDefaultWithoutAVerifier() {
		this.runner.run(context -> {
			assertThat(context.getBean(HumanCheckSetting.class)).isEqualTo(new HumanCheckSetting.Off());
			assertThat(context).doesNotHaveBean(HumanVerifier.class);
			assertThat(context.getBean(HumanCheck.class).enabled()).isFalse();
		});
	}

	@Test
	void aSecretKeyRegistersTheTurnstileVerifierInAnyProfile() {
		this.runner
			.withPropertyValues("yozmnews.human-check.provider=turnstile",
					"yozmnews.human-check.turnstile.site-key=1x00000000000000000000AA",
					"yozmnews.human-check.turnstile.secret-key=" + SECRET)
			.run(context -> {
				assertThat(context.getBean(HumanVerifier.class)).isInstanceOf(TurnstileHumanVerifier.class);
				assertThat(context.getBean(HumanCheck.class).enabled()).isTrue();
			});
		this.runner
			.withPropertyValues("spring.profiles.active=local,web,worker", "yozmnews.human-check.provider=turnstile",
					"yozmnews.human-check.turnstile.site-key=1x00000000000000000000AA",
					"yozmnews.human-check.turnstile.secret-key=" + SECRET)
			.run(context -> assertThat(context).getBean(HumanVerifier.class).isInstanceOf(TurnstileHumanVerifier.class));
	}

	@Test
	void turnedOnWithoutASecretOutsideLocalDevelopmentStopsWithoutQuotingValues() {
		this.runner
			.withPropertyValues("yozmnews.human-check.provider=turnstile",
					"yozmnews.human-check.turnstile.site-key=1x00000000000000000000AA")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.hasMessageContaining("YOZMNEWS_HUMAN_CHECK_TURNSTILE_SECRET_KEY");
			});
	}

	@Test
	void aMissingSiteKeyOrUnknownProviderStopsTheStartup() {
		this.runner
			.withPropertyValues("yozmnews.human-check.provider=turnstile",
					"yozmnews.human-check.turnstile.secret-key=" + SECRET)
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.hasMessageContaining("YOZMNEWS_HUMAN_CHECK_TURNSTILE_SITE_KEY")
					.message()
					.doesNotContain(SECRET);
			});
		this.runner.withPropertyValues("yozmnews.human-check.provider=recaptcha")
			.run(context -> assertThat(context).hasFailed());
	}

}
