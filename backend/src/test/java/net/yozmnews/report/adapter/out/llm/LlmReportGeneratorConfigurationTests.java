package net.yozmnews.report.adapter.out.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.report.adapter.out.fake.FakeReportGeneratorConfigurationAccess;
import net.yozmnews.report.domain.generator.ReportGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 실제 리포트 생성기의 결선 — profile {@code local} 이 아닐 때만 등록된다. 역할 profile 이 있든 없든 하나다. 로컬 대체물의 결선과 함께 두면
 * 어느 profile 에서도 생성기는 꼭 하나다(대조).
 * <p>
 * 지키는 규칙: report-R10 · report-R24
 */
class LlmReportGeneratorConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(LlmReportGeneratorConfiguration.class)
		.withBean(LlmClientFactory.class, () -> mock(LlmClientFactory.class));

	@Test
	void registersTheRealGeneratorWithTheRoleProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web,worker")
			.run(context -> {
				assertThat(context).hasSingleBean(ReportGenerator.class);
				assertThat(context.getBean(ReportGenerator.class)).isInstanceOf(LlmReportGenerator.class);
			});
	}

	@Test
	void registersTheRealGeneratorWithTheDefaultProfiles() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker")
			.run(context -> assertThat(context).hasSingleBean(LlmReportGenerator.class));
	}

	@Test
	void doesNotRegisterTheRealGeneratorInLocalDevelopment() {
		this.runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(ReportGenerator.class));
	}

	@Test
	void withTheLocalSubstituteWiringThereIsExactlyOneGeneratorInEveryProfile() {
		ApplicationContextRunner both = this.runner.withUserConfiguration(FakeReportGeneratorConfigurationAccess.CONFIGURATION);
		both.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker")
			.run(context -> assertThat(context.getBean(ReportGenerator.class).getClass().getSimpleName())
				.isEqualTo("FakeReportGenerator"));
		both.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web,worker")
			.run(context -> assertThat(context.getBean(ReportGenerator.class)).isInstanceOf(LlmReportGenerator.class));
		both.withPropertyValues("spring.profiles.default=web,worker")
			.run(context -> assertThat(context).hasSingleBean(ReportGenerator.class));
	}

}
