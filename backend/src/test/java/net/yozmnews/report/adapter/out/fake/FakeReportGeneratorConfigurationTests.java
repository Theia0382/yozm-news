package net.yozmnews.report.adapter.out.fake;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.report.domain.generator.ReportGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 리포트 생성기 로컬 대체물의 등록 조건 — 켜진 profile 을 바꿔 컨텍스트를 띄워 본다.
 * <p>
 * 지키는 규칙: report-R10 · platform-R18
 */
class FakeReportGeneratorConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(FakeReportGeneratorConfiguration.class);

	@Test
	void registersTheFakeInLocalDevelopment() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker")
			.run(context -> {
				assertThat(context).hasSingleBean(ReportGenerator.class);
				assertThat(context.getBean(ReportGenerator.class)).isInstanceOf(FakeReportGenerator.class);
			});
	}

	@Test
	void doesNotRegisterTheFakeWithOnlyRoleProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(ReportGenerator.class));
	}

	@Test
	void doesNotRegisterTheFakeWithTheDefaultProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(ReportGenerator.class));
	}

}
