package net.yozmnews.evaluation;

import java.util.Map;
import java.util.Objects;

/**
 * 태그 판정의 답 — 질문마다의 확률 분포 · docs/specs/evaluation.md §3 · evaluation-R41.
 * <p>
 * 질문 묶음의 키마다 하나씩 있고 다른 키는 없다 — 관문이 판정할 때 질문 묶음과 대조했다. 대표값 고르기 · 저장 기준 거르기는 부르는 쪽의
 * 규칙이다.
 *
 * @param topics 주제 중분류 전체 키 → 확률
 * @param regions 지역 키 → 확률
 * @param formats 형식 선택지 키 → 확률
 * @param importance 중요도 수준 → 확률
 */
public record TagAnswers(Map<String, Double> topics, Map<String, Double> regions, Map<String, Double> formats,
		Map<Integer, Double> importance) {

	public TagAnswers {
		topics = Map.copyOf(Objects.requireNonNull(topics, "topics"));
		regions = Map.copyOf(Objects.requireNonNull(regions, "regions"));
		formats = Map.copyOf(Objects.requireNonNull(formats, "formats"));
		importance = Map.copyOf(Objects.requireNonNull(importance, "importance"));
	}

}
