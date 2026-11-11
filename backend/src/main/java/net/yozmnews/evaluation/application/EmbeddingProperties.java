package net.yozmnews.evaluation.application;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 임베딩 provider 설정 — docs/specs/evaluation.md §7 · evaluation-R46.
 * <p>
 * 기본값의 정본은 이 레코드의 {@code @DefaultValue} 다. 결선 조건({@link EmbeddingProviderCondition})도 이 레코드로 묶어 읽는다.
 *
 * @param provider 임베더 — 설정 값 {@code onnx}(기본) · {@code openai-compatible}
 */
@ConfigurationProperties(EmbeddingProperties.PREFIX)
public record EmbeddingProperties(@DefaultValue("onnx") Provider provider) {

	/** 설정 이름의 앞부분 */
	public static final String PREFIX = "yozmnews.embedding";

	public EmbeddingProperties {
		Objects.requireNonNull(provider, "provider");
	}

	/** 임베더 종류 */
	public enum Provider {

		/** 앱 안 로컬 모델 */
		ONNX,
		/** OpenAI 호환 외부 API */
		OPENAI_COMPATIBLE

	}

}
