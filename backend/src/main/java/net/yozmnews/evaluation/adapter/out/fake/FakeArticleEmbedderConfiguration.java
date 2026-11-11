package net.yozmnews.evaluation.adapter.out.fake;

import net.yozmnews.evaluation.application.EmbeddingProviderCondition;
import net.yozmnews.evaluation.domain.embedder.ArticleEmbedder;
import net.yozmnews.platform.LocalDevelopment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 임베더 로컬 대체물의 결선 — profile {@code local} 에서만, provider 가 {@code onnx}(기본)일 때 등록한다 · docs/specs/evaluation.md §4 ·
 * evaluation-R47.
 * <p>
 * {@code local} 에서 provider 를 {@code openai-compatible} 로 주면 외부 구현만 등록된다.
 */
@LocalDevelopment
@Configuration(proxyBeanMethods = false)
@Conditional(EmbeddingProviderCondition.Onnx.class)
class FakeArticleEmbedderConfiguration {

	@Bean
	ArticleEmbedder articleEmbedder() {
		return new FakeArticleEmbedder();
	}

}
