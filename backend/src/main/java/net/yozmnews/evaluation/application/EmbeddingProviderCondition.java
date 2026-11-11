package net.yozmnews.evaluation.application;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 임베더 결선 조건의 정본 — 설정 {@code yozmnews.embedding.provider} 를 {@link EmbeddingProperties} 로 묶어 읽는다 · docs/specs/evaluation.md
 * §4 · §7.
 * <p>
 * 기본값과 완화된 enum 변환은 레코드 하나가 정한다. 값이 틀리면 조건 평가에서 바인딩 오류로 기동이 멈춘다 — 문자열 비교였다면 어느 조건에도
 * 맞지 않아 "필요한 빈 없음" 으로만 드러난다. 로컬 모델 · 외부 · 로컬 대체물의 결선이 함께 읽는다.
 */
public final class EmbeddingProviderCondition {

	private EmbeddingProviderCondition() {
	}

	/** 설정의 provider */
	static EmbeddingProperties.Provider provider(ConditionContext context) {
		return Binder.get(context.getEnvironment())
			.bindOrCreate(EmbeddingProperties.PREFIX, EmbeddingProperties.class)
			.provider();
	}

	/** provider 가 {@code onnx}(기본)일 때 */
	public static final class Onnx implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return provider(context) == EmbeddingProperties.Provider.ONNX;
		}

	}

	/** provider 가 {@code openai-compatible} 일 때 */
	public static final class OpenAiCompatible implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return provider(context) == EmbeddingProperties.Provider.OPENAI_COMPATIBLE;
		}

	}

}
