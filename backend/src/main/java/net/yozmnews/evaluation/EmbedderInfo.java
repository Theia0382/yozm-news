package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 임베더가 밝히는 정보 — docs/specs/evaluation.md §3.
 *
 * @param name 임베더 이름(로그용)
 * @param modelVersion 모델을 가리키는 값 — 다른 모델과 겹치지 않는다. 부르는 쪽이 벡터와 함께 남기는 모델 버전이다(evaluation-R45)
 * @param dimensions 벡터 차원
 */
public record EmbedderInfo(String name, String modelVersion, int dimensions) {

	/** 관문이 내는 벡터의 차원 상한 — 부르는 쪽의 장기 검색 인덱스(halfvec HNSW)가 받는 최대 차원 · evaluation-R45 */
	public static final int MAX_DIMENSIONS = 4000;

	/**
	 * @throws IllegalArgumentException 이름 · 모델 버전이 공백뿐이거나 차원이 1 ~ {@value #MAX_DIMENSIONS} 밖일 때
	 */
	public EmbedderInfo {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(modelVersion, "modelVersion");
		if (name.isBlank()) {
			throw new IllegalArgumentException("임베더 이름이 비었다");
		}
		if (modelVersion.isBlank()) {
			throw new IllegalArgumentException("모델 버전이 비었다");
		}
		if (dimensions < 1 || dimensions > MAX_DIMENSIONS) {
			throw new IllegalArgumentException("차원은 1 ~ " + MAX_DIMENSIONS + " 이다: " + dimensions);
		}
	}

}
