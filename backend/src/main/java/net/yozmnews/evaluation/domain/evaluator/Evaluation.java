package net.yozmnews.evaluation.domain.evaluator;

import java.util.Map;
import java.util.Objects;

/**
 * 평가했다 — 태그 종류마다 확률 · docs/specs/evaluation.md §4.
 * <p>
 * 생성자는 값 하나하나만 검사한다. 어느 키 · 수준이 있어야 하는지는 넘긴 질문 묶음가 정하고, 이 레코드는 검사하지 않는다.
 *
 * @param topics 주제 중분류 전체 키 → 확률
 * @param regions 지역 키 → 확률
 * @param formats 형식 키 → 확률
 * @param importance 중요도 수준 → 확률
 * @param evaluator 평가기 이름
 * @param modelVersion 모델 버전
 * @param inputTokens 입력 토큰 수
 * @param rawResponse 원본 응답 — JSON 텍스트
 */
public record Evaluation(Map<String, Double> topics, Map<String, Double> regions, Map<String, Double> formats,
		Map<Integer, Double> importance, String evaluator, String modelVersion, int inputTokens, String rawResponse)
		implements EvaluationResult, RereadResult {

	/**
	 * @throws IllegalArgumentException 키가 공백뿐이거나, 확률이 0 이상 1 이하의 유한값이 아니거나, 이름 · 버전이 공백뿐이거나, 토큰 수가
	 * 음수일 때
	 */
	public Evaluation {
		topics = probabilities(topics, "topics");
		regions = probabilities(regions, "regions");
		formats = probabilities(formats, "formats");
		Objects.requireNonNull(importance, "importance");
		importance.values().forEach(value -> checkProbability(value, "importance"));
		importance = Map.copyOf(importance);
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(rawResponse, "rawResponse");
		if (evaluator.isBlank() || modelVersion.isBlank()) {
			throw new IllegalArgumentException("평가기 이름 · 모델 버전이 비었다");
		}
		if (inputTokens < 0) {
			throw new IllegalArgumentException("입력 토큰 수가 음수다: " + inputTokens);
		}
	}

	private static Map<String, Double> probabilities(Map<String, Double> values, String name) {
		Objects.requireNonNull(values, name);
		values.forEach((key, value) -> {
			if (key.isBlank()) {
				throw new IllegalArgumentException(name + " 의 키가 비었다");
			}
			checkProbability(value, name);
		});
		return Map.copyOf(values);
	}

	private static void checkProbability(Double value, String name) {
		Objects.requireNonNull(value, name);
		if (!Double.isFinite(value) || value < 0 || value > 1) {
			throw new IllegalArgumentException(name + " 의 확률은 0 ~ 1 의 유한값이다: " + value);
		}
	}

}
