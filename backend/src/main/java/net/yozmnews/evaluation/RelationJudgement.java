package net.yozmnews.evaluation;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * 관계 판정 한 번의 결과 — docs/specs/evaluation.md §3 · evaluation-R49
 */
public sealed interface RelationJudgement {

	/**
	 * 판정했다 — 답 다섯의 확률과 흐름마다 한 사안의 전개일 확률.
	 *
	 * @param judgement 남긴 판정 원본
	 * @param probabilities 답 → 확률 — 다섯 답 모두. 합은 계약하지 않는다
	 * @param firstOneMatter first 가 한 사안의 전개일 확률
	 * @param secondOneMatter second 가 한 사안의 전개일 확률
	 */
	record Judged(RelationJudgementId judgement, Map<RelationAnswer, Double> probabilities, double firstOneMatter,
			double secondOneMatter) implements RelationJudgement {

		/**
		 * @throws IllegalArgumentException 답 다섯이 모두 있지 않거나 확률이 0 이상 1 이하의 유한값이 아닐 때
		 */
		public Judged {
			Objects.requireNonNull(judgement, "judgement");
			Objects.requireNonNull(probabilities, "probabilities");
			if (!probabilities.keySet().equals(EnumSet.allOf(RelationAnswer.class))) {
				throw new IllegalArgumentException("답 다섯의 확률이 모두 있어야 한다: " + probabilities.keySet());
			}
			probabilities.values().forEach(Judged::requireProbability);
			requireProbability(firstOneMatter);
			requireProbability(secondOneMatter);
			probabilities = Map.copyOf(probabilities);
		}

		private static void requireProbability(Double probability) {
			Objects.requireNonNull(probability, "probability");
			if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
				throw new IllegalArgumentException("확률은 0 ~ 1 의 유한값이다: " + probability);
			}
		}

	}

	/**
	 * 평가기가 예상한 이유로 판정하지 못했다 — 판정 원본을 남기지 않았다.
	 *
	 * @param message 운영자용 문구 — 비밀 값을 담지 않는다
	 */
	record Failed(EvaluatorFailureKind kind, String message) implements RelationJudgement {

		/**
		 * @throws IllegalArgumentException 문구가 공백뿐일 때
		 */
		public Failed {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(message, "message");
			if (message.isBlank()) {
				throw new IllegalArgumentException("실패 문구가 비었다");
			}
		}

	}

	/** 한쪽 흐름의 서술이 하나도 남지 않았다 — 평가기를 부르지 않았고 판정 원본이 없다 */
	record NothingToAsk() implements RelationJudgement {

	}

}
