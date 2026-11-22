package net.yozmnews.report.adapter.out.fake;

import net.yozmnews.platform.LocalDevelopment;
import net.yozmnews.report.domain.generator.ReportGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 리포트 생성기 로컬 대체물의 결선 — profile {@code local} 에서 언제나 등록한다 · docs/specs/report.md §4 · report-R10
 */
@LocalDevelopment
@Configuration(proxyBeanMethods = false)
class FakeReportGeneratorConfiguration {

	@Bean
	ReportGenerator reportGenerator() {
		return new FakeReportGenerator();
	}

}
