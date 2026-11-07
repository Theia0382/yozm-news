package net.yozmnews.event.adapter.out.onnx;

import net.yozmnews.event.application.EmbeddingProviderCondition;
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import net.yozmnews.platform.LocalDevelopment;
import net.yozmnews.platform.WorkerRole;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 기본 로컬 임베딩의 결선 — 작업 역할에서, profile {@code local} 밖에서, provider 가 {@code onnx}(기본)일 때 등록한다 ·
 * docs/specs/event.md §4 · §7 · event-R7.
 * <p>
 * 모델 · 토크나이저를 열지 못하거나 텔레메트리 변수가 없으면 작업 역할이 뜨지 않는다. {@code local} 은 로컬 대체물이 기본이다.
 */
@WorkerRole
@Configuration(proxyBeanMethods = false)
@Conditional({ EmbeddingProviderCondition.Onnx.class, OnnxArticleEmbedderConfiguration.OutsideLocalDevelopment.class })
class OnnxArticleEmbedderConfiguration {

	/**
	 * profile {@code local} 이 아닐 때 — {@code @Profile} 로 적으면 {@link WorkerRole} 의 {@code @Profile} 과 OR 로 판정되어 {@code local} 에서도
	 * 등록된다. {@code @Conditional} 끼리는 AND 다.
	 */
	static final class OutsideLocalDevelopment implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return !context.getEnvironment().matchesProfiles(LocalDevelopment.PROFILE);
		}

	}

	/**
	 * @throws IllegalStateException 모델 정보 · 파일 · 토크나이저 · 세션을 열지 못했거나 텔레메트리 변수가 없을 때
	 */
	@Bean(destroyMethod = "close")
	ArticleEmbedder onnxArticleEmbedder() {
		try {
			return new OnnxArticleEmbedder(EmbeddingModel.load());
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("임베딩 모델을 읽지 못해 작업 역할을 띄울 수 없다 — " + ex.getClass().getName(), ex);
		}
	}

}
