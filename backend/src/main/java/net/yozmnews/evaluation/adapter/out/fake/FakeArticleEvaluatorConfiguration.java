package net.yozmnews.evaluation.adapter.out.fake;

import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.platform.LocalDevelopment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fake 평가기의 결선 — 로컬 개발에서만 등록한다 · docs/specs/evaluation.md §7 · platform-R18
 */
@LocalDevelopment
@Configuration(proxyBeanMethods = false)
class FakeArticleEvaluatorConfiguration {

	@Bean
	ArticleEvaluator articleEvaluator() {
		return new FakeArticleEvaluator();
	}

}
