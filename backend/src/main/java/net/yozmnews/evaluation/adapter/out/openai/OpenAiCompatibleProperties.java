package net.yozmnews.evaluation.adapter.out.openai;

import java.net.URI;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OpenAI 호환 외부 임베딩 설정 — docs/specs/evaluation.md §7 · docs/operations/configuration.md.
 * <p>
 * 기본값의 정본은 이 레코드의 {@code @DefaultValue} 다. 필수 값 · 범위는 결선({@link OpenAiCompatibleEmbedderConfiguration})이 본다.
 *
 * @param baseUrl 기본 주소 — {@code /embeddings} 를 그 뒤에 붙인다. 필수
 * @param model 모델 이름. 필수
 * @param apiKey API 키 — 비밀 값. 없거나 비면 {@code Authorization} 을 보내지 않는다
 * @param dimensions 벡터 차원. 필수
 * @param sendDimensions true 면 요청에 {@code dimensions} 를 담는다
 * @param inputPrefix 텍스트마다 앞에 붙일 문자열
 * @param batchSize 한 요청의 입력 수
 */
@ConfigurationProperties(OpenAiCompatibleProperties.PREFIX)
record OpenAiCompatibleProperties(@Nullable URI baseUrl, @Nullable String model, @Nullable String apiKey,
		@Nullable Integer dimensions, @DefaultValue("false") boolean sendDimensions,
		@DefaultValue("") String inputPrefix, @DefaultValue("32") int batchSize) {

	/** 설정 이름의 앞부분 */
	static final String PREFIX = "yozmnews.embedding.openai-compatible";

	@Override
	public String toString() {
		return "OpenAiCompatibleProperties[baseUrl=" + this.baseUrl + ", model=" + this.model + ", apiKey="
				+ ((this.apiKey == null || this.apiKey.isEmpty()) ? "없음" : "숨김") + ", dimensions=" + this.dimensions
				+ ", sendDimensions=" + this.sendDimensions + ", inputPrefix=" + this.inputPrefix + ", batchSize="
				+ this.batchSize + "]";
	}

}
