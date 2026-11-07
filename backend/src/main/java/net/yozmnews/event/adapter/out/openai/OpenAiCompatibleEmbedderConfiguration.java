package net.yozmnews.event.adapter.out.openai;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import net.yozmnews.event.application.EmbeddingProviderCondition;
import net.yozmnews.event.domain.feature.ArticleEmbedder;
import net.yozmnews.event.domain.feature.EmbedderInfo;
import net.yozmnews.event.domain.openai.EmbeddingApiKey;
import net.yozmnews.platform.WorkerRole;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI 호환 외부 임베딩의 결선 — 작업 역할에서, provider 가 {@code openai-compatible} 일 때 등록한다(모든 profile) ·
 * docs/specs/event.md §4 · §7 · event-R7.
 * <p>
 * 필수 값 · 형식이 틀리면 작업 역할이 뜨지 않는다. 문구에는 설정 이름 · 환경 변수 이름만 있고 키 값은 없다.
 */
@WorkerRole
@Configuration(proxyBeanMethods = false)
@Conditional(EmbeddingProviderCondition.OpenAiCompatible.class)
@EnableConfigurationProperties(OpenAiCompatibleProperties.class)
class OpenAiCompatibleEmbedderConfiguration {

	/** 한 요청의 입력 수 상한 */
	static final int MAX_BATCH_SIZE = 256;

	private static final Set<String> SCHEMES = Set.of("http", "https");

	/**
	 * @throws IllegalStateException 필수 값이 없거나 형식 · 범위가 틀렸을 때 — 문구에 키 값은 없다
	 */
	@Bean(destroyMethod = "close")
	ArticleEmbedder openAiCompatibleEmbedder(OpenAiCompatibleProperties properties) {
		URI baseUrl = properties.baseUrl();
		if (baseUrl == null || baseUrl.getScheme() == null || !SCHEMES.contains(baseUrl.getScheme())
				|| baseUrl.getHost() == null || baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null) {
			throw invalid("base-url", "은 호스트가 있고 질의 · 조각이 없는 http · https 주소여야 한다");
		}
		String model = properties.model();
		if (model == null || model.isBlank()) {
			throw invalid("model", "이 비었다");
		}
		Integer dimensions = properties.dimensions();
		if (dimensions == null || dimensions < 1 || dimensions > EmbedderInfo.MAX_DIMENSIONS) {
			throw invalid("dimensions", "은 1 ~ " + EmbedderInfo.MAX_DIMENSIONS + " 이어야 한다");
		}
		if (properties.batchSize() < 1 || properties.batchSize() > MAX_BATCH_SIZE) {
			throw invalid("batch-size", "는 1 ~ " + MAX_BATCH_SIZE + " 이어야 한다");
		}
		return new OpenAiCompatibleEmbedder(baseUrl, model, key(properties.apiKey()), dimensions,
				properties.sendDimensions(), properties.inputPrefix(), properties.batchSize());
	}

	private static @Nullable EmbeddingApiKey key(@Nullable String value) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		try {
			return EmbeddingApiKey.of(value);
		}
		catch (IllegalArgumentException ex) {
			throw invalid("api-key", "가 틀렸다 — " + ex.getMessage());
		}
	}

	/** 설정 이름과 환경 변수 이름을 담은 기동 오류 */
	private static IllegalStateException invalid(String name, String problem) {
		String property = OpenAiCompatibleProperties.PREFIX + "." + name;
		String variable = property.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
		return new IllegalStateException(property + "(환경 변수 " + variable + ")" + problem);
	}

}
