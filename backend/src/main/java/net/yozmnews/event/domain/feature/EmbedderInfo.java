package net.yozmnews.event.domain.feature;

import java.util.Objects;

/**
 * 임베더가 밝히는 정보 — docs/specs/event.md §4.
 *
 * @param name 임베더 이름(로그용)
 * @param modelVersion 모델을 가리키는 값 — 다른 모델과 겹치지 않는다. 표현 행의 model_version(event-R4)
 * @param dimensions 벡터 차원
 */
public record EmbedderInfo(String name, String modelVersion, int dimensions) {

	/**
	 * @throws IllegalArgumentException 이름 · 모델 버전이 공백뿐이거나 차원이 1 미만일 때
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
		if (dimensions < 1) {
			throw new IllegalArgumentException("차원은 1 이상이다: " + dimensions);
		}
	}

}
