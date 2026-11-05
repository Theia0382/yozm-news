package net.yozmnews.event.adapter.out.fake;

import net.yozmnews.event.domain.feature.ArticleEmbedder;
import net.yozmnews.platform.LocalDevelopment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 임베더 로컬 대체물의 결선 — profile {@code local} 에서만 등록한다 · docs/specs/event.md §4 · event-R9
 */
@LocalDevelopment
@Configuration(proxyBeanMethods = false)
class FakeArticleEmbedderConfiguration {

	@Bean
	ArticleEmbedder articleEmbedder() {
		return new FakeArticleEmbedder();
	}

}
