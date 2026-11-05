package net.yozmnews.event.domain.feature;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 기사 표현 한 행 — (기사, 모델 버전)마다 하나 · docs/specs/event.md §4.
 *
 * @param modelVersion 임베더가 밝힌 모델 버전
 * @param entities 핵심 말 — {@link KeyTermExtractor} 결과 그대로, 비어 있을 수 있다(event-R2)
 * @param createdAt 만든 시각
 */
public record MatchFeatures(ArticleId articleId, String modelVersion, Embedding embedding, List<String> entities,
		Instant createdAt) {

	/**
	 * @throws IllegalArgumentException 모델 버전이 공백뿐일 때
	 */
	public MatchFeatures {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(embedding, "embedding");
		Objects.requireNonNull(createdAt, "createdAt");
		if (modelVersion.isBlank()) {
			throw new IllegalArgumentException("모델 버전이 비었다");
		}
		entities = List.copyOf(entities);
	}

}
