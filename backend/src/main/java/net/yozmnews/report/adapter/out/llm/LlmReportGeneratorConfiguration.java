package net.yozmnews.report.adapter.out.llm;

import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.platform.LocalDevelopment;
import net.yozmnews.report.domain.generator.ReportGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 실제 리포트 생성기의 결선 — profile {@code local} 밖에서 역할과 무관하게 등록한다({@code local} 은 로컬 대체물) · docs/specs/report.md §4 ·
 * report-R24
 */
@Profile("!" + LocalDevelopment.PROFILE)
@Configuration(proxyBeanMethods = false)
class LlmReportGeneratorConfiguration {

	@Bean
	ReportGenerator reportGenerator(LlmClientFactory gateway) {
		return new LlmReportGenerator(gateway);
	}

}
