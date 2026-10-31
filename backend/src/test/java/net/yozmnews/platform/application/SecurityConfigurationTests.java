package net.yozmnews.platform.application;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.platform.ProblemResponses;
import net.yozmnews.platform.domain.AdminCredential;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 구성의 기동 — 잘못된 운영자 토큰은 기동을 멈추고, 기본 사용자 · 생성 비밀번호가 생기지 않는다.
 * <p>
 * 지키는 규칙: 운영자 접근 통제(docs/specs/platform.md §5)
 */
class SecurityConfigurationTests {

	private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, SecurityAutoConfiguration.class,
				ServletWebSecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class))
		.withUserConfiguration(SecurityConfiguration.class, ProblemResponses.class);

	@Test
	void malformedTokenStopsTheStartupWithoutQuotingIt() {
		String short31 = "S3cr3tValueOfThirtyOneCharsXyzQ";

		this.runner.withPropertyValues("yozmnews.admin.token=" + short31).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause()
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("운영자 토큰은 32자 이상 512자 이하여야 한다 (지금 31자)");
			assertThat(context.getStartupFailure()).hasStackTraceContaining("adminCredential")
				.satisfies(failure -> assertThat(stackTrace(failure)).doesNotContain(short31));
		});
	}

	@Test
	void wellFormedTokenOpensTheAdminFunctions() {
		this.runner.withPropertyValues("yozmnews.admin.token=" + AdminSecurityTests.TOKEN).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(AdminCredential.class).open()).isTrue();
		});
	}

	@Test
	void noTokenStartsWithTheAdminFunctionsClosed() {
		this.runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(AdminCredential.class).open()).isFalse();
		});
	}

	@Test
	void createsNoDefaultUserAndOnlyTheTwoChains() {
		this.runner.run(context -> {
			assertThat(context).doesNotHaveBean(UserDetailsService.class);
			assertThat(context.getBeansOfType(SecurityFilterChain.class)).containsOnlyKeys("adminChain", "defaultChain");
		});
	}

	private static String stackTrace(Throwable failure) {
		java.io.StringWriter text = new java.io.StringWriter();
		failure.printStackTrace(new java.io.PrintWriter(text));
		return text.toString();
	}

}
