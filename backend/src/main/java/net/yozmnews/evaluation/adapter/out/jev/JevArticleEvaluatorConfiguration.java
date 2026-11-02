package net.yozmnews.evaluation.adapter.out.jev;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.jev.JevApiKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Jev 평가기의 결선 — 키가 설정되면 모든 profile 에서 등록한다 · docs/specs/evaluation.md §7.
 * <p>
 * 키가 없을 때의 동작은 로컬 대체물 결선(profile {@code local})과 작업 역할 기동 검사({@link JevKeyRequirement})가 정한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JevProperties.class)
class JevArticleEvaluatorConfiguration {

	private static final Set<String> SCHEMES = Set.of("http", "https");

	/**
	 * @throws IllegalStateException 키 형식 · 모델 · 기본 주소가 틀렸을 때 — 문구에 설정 이름과 환경 변수 이름이 있고, 키 값은 없다
	 */
	@Bean(destroyMethod = "close")
	@Conditional(JevKeyCondition.Present.class)
	ArticleEvaluator jevArticleEvaluator(JevProperties properties) {
		JevApiKey key;
		try {
			key = JevApiKey.of(Objects.requireNonNull(properties.apiKey(), JevKeyCondition.PROPERTY));
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(
					JevKeyCondition.PROPERTY + "(환경 변수 " + JevKeyCondition.ENVIRONMENT_VARIABLE + ")가 틀렸다 — "
							+ ex.getMessage(), ex);
		}
		String model = properties.model();
		if (model == null || model.isBlank()) {
			throw new IllegalStateException("yozmnews.jev.model(환경 변수 YOZMNEWS_JEV_MODEL)이 비었다");
		}
		URI baseUrl = properties.baseUrl();
		if (baseUrl == null || baseUrl.getScheme() == null || !SCHEMES.contains(baseUrl.getScheme())
				|| baseUrl.getHost() == null || baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null) {
			throw new IllegalStateException("yozmnews.jev.base-url(환경 변수 YOZMNEWS_JEV_BASE_URL)은 호스트가 있고 "
					+ "질의 · 조각이 없는 http · https 주소여야 한다");
		}
		return new JevArticleEvaluator(baseUrl, model, key);
	}

}
