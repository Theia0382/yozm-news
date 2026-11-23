package net.yozmnews.user.adapter.out.fake;

import net.yozmnews.platform.LocalDevelopment;
import net.yozmnews.user.domain.summary.StorylineSummarizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 정리 생성기 로컬 대체물의 결선 — profile {@code local} 에서 조건 없이 등록한다. docs/specs/user.md §4
 */
@LocalDevelopment
@Configuration(proxyBeanMethods = false)
class FakeStorylineSummarizerConfiguration {

	@Bean
	StorylineSummarizer storylineSummarizer() {
		return new FakeStorylineSummarizer();
	}

}
