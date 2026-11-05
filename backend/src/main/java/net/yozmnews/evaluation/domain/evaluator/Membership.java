package net.yozmnews.evaluation.domain.evaluator;

import java.util.Map;
import java.util.Objects;

/**
 * 사건 소속을 판정했다 — 후보 사건마다 확률 · docs/specs/evaluation.md §4.
 * <p>
 * 생성자는 값 하나하나만 검사한다. 물은 후보와 키가 맞는지는 부르는 쪽이 본다.
 *
 * @param probabilities 후보 사건 ID → 그 사건에 속할 확률
 * @param evaluator 평가기 이름
 * @param modelVersion 모델 버전
 * @param inputTokens 입력 토큰 수
 * @param rawResponse 원본 응답 — JSON 텍스트
 */
public record Membership(Map<Long, Double> probabilities, String evaluator, String modelVersion, int inputTokens,
		String rawResponse) implements MembershipResult {

	/**
	 * @throws IllegalArgumentException 확률이 0 이상 1 이하의 유한값이 아니거나, 이름 · 버전이 공백뿐이거나, 토큰 수가 음수일 때
	 */
	public Membership {
		Objects.requireNonNull(probabilities, "probabilities");
		probabilities.forEach((eventId, probability) -> {
			Objects.requireNonNull(eventId, "eventId");
			Objects.requireNonNull(probability, "probability");
			if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
				throw new IllegalArgumentException("확률은 0 ~ 1 의 유한값이다: " + probability);
			}
		});
		probabilities = Map.copyOf(probabilities);
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

}
