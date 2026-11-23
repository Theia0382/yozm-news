package net.yozmnews.user.adapter.out.llm;

import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.platform.LocalDevelopment;
import net.yozmnews.user.domain.summary.StorylineSummarizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 실제 정리 생성기의 결선 — profile {@code local} 밖에서 등록한다({@code local} 은 로컬 대체물). docs/specs/user.md §4
 */
@Profile("!" + LocalDevelopment.PROFILE)
@Configuration(proxyBeanMethods = false)
class LlmStorylineSummarizerConfiguration {

	@Bean
	StorylineSummarizer storylineSummarizer(LlmClientFactory gateway) {
		return new LlmStorylineSummarizer(gateway);
	}

}
